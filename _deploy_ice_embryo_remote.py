# -*- coding: utf-8 -*-
"""Compile Cryo Traveler ice-infusion fix against remote jar, inject, sync resources, restart."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tarfile
import time
import zipfile
from datetime import datetime
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
HOST = "qianzhi-211"
REMOTE_JAR = "/www/wwwroot/LunaGC-src-run.jar"
REMOTE_RES = "/www/wwwroot/resources"
REMOTE_SHIP = "/tmp/ice_embryo_ship"
REMOTE_INJECT = "/tmp/_remote_inject_ice_embryo.py"

JAVA = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe")
LOMBOK = ROOT / "lombok-1.18.30.jar"
if not LOMBOK.exists():
    LOMBOK = ROOT / "lombok.jar"

OUT = ROOT / "_ice_embryo_remote_classes"
SHIP = ROOT / "_ice_embryo_ship"
ERR = ROOT / "_ice_embryo_remote_javac_err.txt"
REMOTE_JAR_LOCAL = ROOT / "_LunaGC-src-run.remote-ice.jar"

SRCS = [
    ROOT / "src/main/java/emu/grasscutter/game/ability/IceTravelerAbilityHelper.java",
    ROOT / "src/main/java/emu/grasscutter/game/ability/PredicateEvaluator.java",
    ROOT / "src/main/java/emu/grasscutter/game/entity/EntityAvatar.java",
]
PREFIXES = (
    "emu/grasscutter/game/ability/IceTravelerAbilityHelper",
    "emu/grasscutter/game/ability/PredicateEvaluator",
    "emu/grasscutter/game/entity/EntityAvatar",
)
RES_FILES = [
    (
        ROOT
        / "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Player_Ice.json",
        f"{REMOTE_RES}/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Player_Ice.json",
    ),
    (
        ROOT / "resources/BinOutput/Gadget/ConfigGadget_SkillObj_Player_Ice.json",
        f"{REMOTE_RES}/BinOutput/Gadget/ConfigGadget_SkillObj_Player_Ice.json",
    ),
]

REMOTE_INJECT_PY = r'''#!/usr/bin/env python3
import os, shutil, time, zipfile
from pathlib import Path

JAR = Path("/www/wwwroot/LunaGC-src-run.jar")
SRC = Path("/tmp/ice_embryo_ship")
TMP = Path("/www/wwwroot/LunaGC-src-run.jar.tmp-ice-embryo")
TS = time.strftime("%Y%m%d%H%M%S")
BAK = Path(f"/www/wwwroot/LunaGC-src-run.jar.bak-before-ice-embryo-{TS}")
PREFIXES = (
    "emu/grasscutter/game/ability/IceTravelerAbilityHelper",
    "emu/grasscutter/game/ability/PredicateEvaluator",
    "emu/grasscutter/game/entity/EntityAvatar",
)

def main():
    adds = {}
    for p in SRC.rglob("*.class"):
        rel = p.relative_to(SRC).as_posix()
        if any(rel.startswith(pref) for pref in PREFIXES):
            adds[rel] = p
            print("ship", rel, p.stat().st_size)
    if len(adds) < 3:
        print("too few classes", len(adds))
        return 1
    shutil.copy2(JAR, BAK)
    print("bak", BAK)
    seen = set()
    if TMP.exists():
        TMP.unlink()
    with zipfile.ZipFile(JAR, "r") as zin, zipfile.ZipFile(TMP, "w", compression=zipfile.ZIP_DEFLATED) as zout:
        skip = set(adds)
        for info in zin.infolist():
            n = info.filename.replace("\\", "/")
            if n in skip or n in seen:
                continue
            seen.add(n)
            zout.writestr(info, zin.read(info.filename))
        for rel, full in adds.items():
            zout.write(full, rel)
    os.replace(TMP, JAR)
    z = zipfile.ZipFile(JAR)
    d = z.read("emu/grasscutter/game/ability/IceTravelerAbilityHelper.class")
    print("remote_helper", b"appendSkillUpgradeEmbryos" in d)
    d2 = z.read("emu/grasscutter/game/entity/EntityAvatar.class")
    print("remote_entity_avatar_ice", b"IceTravelerAbilityHelper" in d2)
    print("ok")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
'''


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    print("+", " ".join(str(c) for c in cmd))
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        raise SystemExit(r.returncode)
    return r


def fetch_remote_jar() -> None:
    run(["scp", "-o", "BatchMode=yes", f"{HOST}:{REMOTE_JAR}", str(REMOTE_JAR_LOCAL)])


def compile_against_remote_jar() -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    classes = OUT / "classes"
    classes.mkdir()

    cp_parts = [str(REMOTE_JAR_LOCAL)]
    if LOMBOK.exists():
        cp_parts.insert(0, str(LOMBOK))
    gen = ROOT / "src" / "generated" / "main" / "java"
    if gen.exists():
        cp_parts.append(str(gen))
    cp = os.pathsep.join(cp_parts)

    cmd = [
        str(JAVA),
        "--release",
        "17",
        "-encoding",
        "UTF-8",
        "-cp",
        cp,
        "-d",
        str(classes),
    ]
    if LOMBOK.exists():
        cmd.extend(["-processorpath", str(LOMBOK)])
    cmd.extend(str(p) for p in SRCS)

    r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True)
    ERR.write_text(
        f"return={r.returncode}\n{(r.stderr or b'').decode('utf-8', 'replace')}\n"
        f"{(r.stdout or b'').decode('utf-8', 'replace')}",
        encoding="utf-8",
    )
    if r.returncode != 0:
        sys.stderr.buffer.write((r.stderr or b"")[-8000:])
        raise SystemExit(r.returncode)

    if SHIP.exists():
        shutil.rmtree(SHIP)
    SHIP.mkdir(parents=True)
    count = 0
    for p in classes.rglob("*.class"):
        rel = p.relative_to(classes).as_posix()
        if any(rel.startswith(pref) for pref in PREFIXES):
            dest = SHIP / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, dest)
            print("pack", rel)
            count += 1
    if count < 3:
        raise RuntimeError(f"too few classes packed: {count}")
    data = (SHIP / "emu/grasscutter/game/ability/IceTravelerAbilityHelper.class").read_bytes()
    if b"appendSkillUpgradeEmbryos" not in data:
        raise RuntimeError("helper missing appendSkillUpgradeEmbryos")


def upload_inject_resources_restart() -> None:
    tar_path = ROOT / "_ice_embryo_ship.tar"
    if tar_path.exists():
        tar_path.unlink()
    with tarfile.open(tar_path, "w") as tar:
        for p in SHIP.rglob("*.class"):
            tar.add(p, arcname=p.relative_to(SHIP).as_posix())

    inject_local = ROOT / "_remote_inject_ice_embryo.py"
    inject_local.write_text(REMOTE_INJECT_PY.replace("\r\n", "\n"), encoding="utf-8")

    run(["scp", "-o", "BatchMode=yes", str(tar_path), f"{HOST}:/tmp/ice_embryo_ship.tar"])
    run(["scp", "-o", "BatchMode=yes", str(inject_local), f"{HOST}:{REMOTE_INJECT}"])

    for local, remote in RES_FILES:
        if not local.exists():
            raise FileNotFoundError(local)
        # backup remote then upload
        run(
            [
                "ssh",
                "-o",
                "BatchMode=yes",
                HOST,
                f"cp -a '{remote}' '{remote}.bak-before-ice-embryo-{datetime.now().strftime('%Y%m%d%H%M%S')}' 2>/dev/null || true",
            ]
        )
        run(["scp", "-o", "BatchMode=yes", str(local), f"{HOST}:{remote}"])

    run(
        [
            "ssh",
            "-o",
            "BatchMode=yes",
            HOST,
            f"rm -rf {REMOTE_SHIP} && mkdir -p {REMOTE_SHIP} && "
            f"tar -xf /tmp/ice_embryo_ship.tar -C {REMOTE_SHIP} && "
            f"sed -i 's/\\r$//' {REMOTE_INJECT} && python3 {REMOTE_INJECT}",
        ]
    )

    run(
        [
            "ssh",
            "-o",
            "BatchMode=yes",
            HOST,
            "bash -lc '"
            "cd /www/wwwroot && "
            "pkill -TERM -f \"LunaGC-src-run.jar\" || true; "
            "for i in $(seq 1 30); do pgrep -f LunaGC-src-run.jar >/dev/null || break; sleep 1; done; "
            "pgrep -f LunaGC-src-run.jar >/dev/null && pkill -KILL -f LunaGC-src-run.jar || true; "
            "sleep 2; "
            "nohup bash /www/wwwroot/start-lunagc-src.sh >/www/wwwroot/logs/ice-embryo-restart.log 2>&1 & "
            "sleep 8; "
            "pgrep -af LunaGC-src-run.jar | head -3; "
            "python3 -c \"import zipfile; z=zipfile.ZipFile(\\\"/www/wwwroot/LunaGC-src-run.jar\\\"); "
            "d=z.read(\\\"emu/grasscutter/game/ability/IceTravelerAbilityHelper.class\\\"); "
            "print(\\\"verify_helper\\\", b\\\"appendSkillUpgradeEmbryos\\\" in d); "
            "e=z.read(\\\"emu/grasscutter/game/entity/EntityAvatar.class\\\"); "
            "print(\\\"verify_avatar\\\", b\\\"IceTravelerAbilityHelper\\\" in e); "
            "import os; p=\\\"/www/wwwroot/resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Player_Ice.json\\\"; "
            "print(\\\"verify_res_size\\\", os.path.getsize(p))\" "
            "'",
        ]
    )


def main() -> int:
    if not JAVA.exists():
        print("javac missing")
        return 1
    fetch_remote_jar()
    compile_against_remote_jar()
    upload_inject_resources_restart()
    print("DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
