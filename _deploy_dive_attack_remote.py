# -*- coding: utf-8 -*-
"""Restore Fontaine underwater attack embryos on remote LunaGC-src-run.jar.

Replaces GameConstants so Dive_Team / Absorb stay, FluidAgitator / DiveVolume stay out,
and the prior javassist clinit filter is removed. Then restarts the remote server.
"""
from __future__ import annotations

import shutil
import subprocess
import tarfile
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
HOST = "qianzhi-211"
REMOTE_WORK = "/tmp/dive_attack_build"
REMOTE_SCRIPT = "/tmp/_remote_build_inject_dive_attack.py"
SRC_REL = "src/main/java/emu/grasscutter/GameConstants.java"

REMOTE_PY = r'''#!/usr/bin/env python3
import os, shutil, subprocess, time, zipfile
from pathlib import Path

JAR = Path("/www/wwwroot/LunaGC-src-run.jar")
WORK = Path("/tmp/dive_attack_build")
SRC = WORK / "src"
OUT = WORK / "classes"
TS = time.strftime("%Y%m%d%H%M%S")
BAK = Path(f"/www/wwwroot/LunaGC-src-run.jar.bak-before-dive-attack-{TS}")
TMP = Path("/www/wwwroot/LunaGC-src-run.jar.tmp-dive-attack")

def find_javac():
    for p in (
        "/usr/lib/jvm/jdk-26/bin/javac",
        "/usr/lib/jvm/jdk-26.0.2.1/bin/javac",
        "/usr/bin/javac",
    ):
        if Path(p).exists():
            return p
    return shutil.which("javac") or "javac"

def main():
    src_file = SRC / "main/java/emu/grasscutter/GameConstants.java"
    if not src_file.exists():
        src_file = SRC / "emu/grasscutter/GameConstants.java"
    assert src_file.exists(), src_file
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    javac = find_javac()
    cmd = [javac, "-encoding", "UTF-8", "-cp", str(JAR), "-d", str(OUT), str(src_file)]
    print("+", " ".join(cmd))
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr)
        raise SystemExit(r.returncode)
    cls = OUT / "emu/grasscutter/GameConstants.class"
    assert cls.exists(), cls
    data = cls.read_bytes()
    assert b"Ability_Avatar_Dive_Team" in data
    assert b"ActivityAbility_Absorb_Shoot" in data
    assert b"Avatar_FluidAgitator" not in data
    assert b"SceneAbility_DiveVolume" not in data
    shutil.copy2(JAR, BAK)
    print("bak", BAK)
    rel = "emu/grasscutter/GameConstants.class"
    if TMP.exists():
        TMP.unlink()
    seen = set()
    with zipfile.ZipFile(JAR, "r") as zin, zipfile.ZipFile(TMP, "w", compression=zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            n = info.filename.replace("\\", "/")
            if n == rel or n in seen:
                continue
            seen.add(n)
            zout.writestr(info, zin.read(info.filename))
        zout.write(cls, rel)
    os.replace(TMP, JAR)
    verify = zipfile.ZipFile(JAR).read(rel)
    assert b"Ability_Avatar_Dive_Team" in verify
    assert b"Avatar_FluidAgitator" not in verify
    print("injected", rel, "size", len(verify))

    subprocess.call(["bash", "-lc", "pkill -TERM -f 'LunaGC-src-run.jar' || true"])
    for _ in range(30):
        if subprocess.call(["bash", "-lc", "pgrep -f 'java.*LunaGC-src-run.jar' >/dev/null"]) != 0:
            break
        time.sleep(1)
    subprocess.call(["bash", "-lc", "pkill -KILL -f 'LunaGC-src-run.jar' || true"])
    time.sleep(2)
    Path("/www/wwwroot/logs").mkdir(parents=True, exist_ok=True)
    subprocess.Popen(
        ["bash", "/www/wwwroot/start-lunagc-src.sh"],
        cwd="/www/wwwroot",
        stdout=open("/www/wwwroot/logs/dive-attack-restart.log", "ab"),
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    time.sleep(12)
    subprocess.call(["bash", "-lc", "pgrep -af 'java.*LunaGC-src-run.jar' | head -3 || echo NO_JAVA"])
    print("DONE")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
'''


def run(cmd: list[str]) -> None:
    print("+", " ".join(str(c) for c in cmd))
    r = subprocess.run(cmd)
    if r.returncode != 0:
        raise SystemExit(r.returncode)


def main() -> int:
    src = ROOT / SRC_REL
    if not src.exists():
        raise SystemExit(f"missing {SRC_REL}")

    tar_path = ROOT / "_dive_attack_ship.tar"
    if tar_path.exists():
        tar_path.unlink()
    with tarfile.open(tar_path, "w") as tar:
        tar.add(src, arcname=SRC_REL.replace("\\", "/"))
        print("pack", SRC_REL)

    script = ROOT / "_remote_build_inject_dive_attack.py"
    script.write_text(REMOTE_PY.replace("\r\n", "\n"), encoding="utf-8")

    run(["scp", "-o", "BatchMode=yes", str(tar_path), f"{HOST}:/tmp/dive_attack_ship.tar"])
    run(["scp", "-o", "BatchMode=yes", str(script), f"{HOST}:{REMOTE_SCRIPT}"])
    run(
        [
            "ssh",
            "-o",
            "BatchMode=yes",
            HOST,
            f"rm -rf {REMOTE_WORK} && mkdir -p {REMOTE_WORK} && "
            f"tar -xf /tmp/dive_attack_ship.tar -C {REMOTE_WORK} && "
            f"sed -i 's/\\r$//' {REMOTE_SCRIPT} && python3 {REMOTE_SCRIPT}",
        ]
    )
    run(
        [
            "ssh",
            "-o",
            "BatchMode=yes",
            HOST,
            "/usr/lib/jvm/jdk-26/bin/javap -classpath /www/wwwroot/LunaGC-src-run.jar -verbose "
            "emu.grasscutter.GameConstants 2>/dev/null | "
            "grep -E 'Ability_Avatar_Dive_Team|Avatar_FluidAgitator|SceneAbility_DiveVolume|HashSet' | head -30",
        ]
    )
    print("LOCAL_DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
