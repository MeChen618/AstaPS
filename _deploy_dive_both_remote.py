# -*- coding: utf-8 -*-
"""Both: no land ripple + underwater combat.

Absorb/Dive_Team stay OUT of defaults (anyone's login/team/death rebuild = ripple).
DiveAbilityHelper dynamically attaches on dive, detaches only on clearly-land motions.
Also blocks CreateGadget(40034001) on land.
"""
from __future__ import annotations

import subprocess
import tarfile
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
HOST = "qianzhi-211"
REMOTE_WORK = "/tmp/dive_both_build"
REMOTE_SCRIPT = "/tmp/_remote_inject_dive_both.py"

SRCS = [
    "src/main/java/emu/grasscutter/GameConstants.java",
    "src/main/java/emu/grasscutter/game/player/DiveAbilityHelper.java",
    "src/main/java/emu/grasscutter/game/ability/actions/ActionCreateGadget.java",
]

REMOTE_PY = r'''#!/usr/bin/env python3
import os, shutil, subprocess, time, zipfile
from pathlib import Path

JAR = Path("/www/wwwroot/LunaGC-src-run.jar")
WORK = Path("/tmp/dive_both_build")
SRC = WORK / "src"
OUT = WORK / "classes"
TS = time.strftime("%Y%m%d%H%M%S")
BAK = Path(f"/www/wwwroot/LunaGC-src-run.jar.bak-before-dive-both-{TS}")
TMP = Path("/www/wwwroot/LunaGC-src-run.jar.tmp-dive-both")

def find_javac():
    for p in ("/usr/lib/jvm/jdk-26/bin/javac", "/usr/bin/javac"):
        if Path(p).exists():
            return p
    return shutil.which("javac") or "javac"

def main():
    java_files = sorted(SRC.rglob("*.java"))
    assert java_files, "no java sources"
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    javac = find_javac()
    cmd = [javac, "-encoding", "UTF-8", "-cp", str(JAR), "-d", str(OUT)] + [str(p) for p in java_files]
    print("+", " ".join(cmd[:6]), f"... ({len(java_files)} files)")
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout); print(r.stderr); raise SystemExit(r.returncode)

    adds = {}
    for p in OUT.rglob("*.class"):
        rel = p.relative_to(OUT).as_posix()
        if (
            rel.startswith("emu/grasscutter/GameConstants")
            or rel.startswith("emu/grasscutter/game/player/DiveAbilityHelper")
            or rel.startswith("emu/grasscutter/game/ability/actions/ActionCreateGadget")
        ):
            adds[rel] = p
            print("built", rel, p.stat().st_size)

    gc = (OUT / "emu/grasscutter/GameConstants.class").read_bytes()
    assert b"DIVE_AVATAR_ABILITIES" in gc
    assert b"ActivityAbility_Absorb_Shoot" in gc
    # Absorb must NOT be in DEFAULT clinit list adjacent permanently — check helper handles it
    helper = (OUT / "emu/grasscutter/game/player/DiveAbilityHelper.class").read_bytes()
    assert b"ensureAvatarDiveAbilities" in helper or b"appendDiveEmbryos" in helper
    assert b"isClearlyLandMotion" in helper
    acg = (OUT / "emu/grasscutter/game/ability/actions/ActionCreateGadget.class").read_bytes()
    assert b"isCurrentlyDiving" in acg
    assert b"DiveAbilityHelper" in acg

    # Verify Absorb_Shoot is referenced from DIVE_AVATAR path: javap strings
    r = subprocess.run(
        [find_javac().replace("javac", "javap"), "-classpath", str(OUT), "-c", "-p",
         "emu.grasscutter.GameConstants"],
        capture_output=True, text=True,
    )
    # Find whether Absorb appears after DIVE or only in array - soft check:
    print("javap Absorb lines:")
    for line in (r.stdout or "").splitlines():
        if "Absorb" in line or "DiveStamina" in line or "Dive_Team" in line or "GrapplingHook" in line:
            print(" ", line.strip())

    shutil.copy2(JAR, BAK)
    print("bak", BAK)
    if TMP.exists():
        TMP.unlink()
    seen = set()
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
            print("inject", rel)
    os.replace(TMP, JAR)
    print("VERIFY_OK", JAR.stat().st_size, "classes", len(adds))

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
        stdout=open("/www/wwwroot/logs/dive-both-restart.log", "ab"),
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    time.sleep(14)
    subprocess.call(["bash", "-lc", "pgrep -af 'java.*LunaGC-src-run.jar' | head -2 || echo NO_JAVA"])
    subprocess.call([
        "bash", "-lc",
        "/usr/lib/jvm/jdk-26/bin/javap -classpath /www/wwwroot/LunaGC-src-run.jar -c -p "
        "emu.grasscutter.GameConstants 2>/dev/null | grep -n 'DiveStamina\\|Absorb_Shoot\\|Dive_Team\\|GrapplingHook\\|DIVE_AVATAR' | head -40"
    ])
    print("DONE")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
'''


def run(cmd):
    print("+", " ".join(str(c) for c in cmd))
    r = subprocess.run(cmd)
    if r.returncode != 0:
        raise SystemExit(r.returncode)


def main():
    tar_path = ROOT / "_dive_both_ship.tar"
    if tar_path.exists():
        tar_path.unlink()
    with tarfile.open(tar_path, "w") as tar:
        for rel in SRCS:
            tar.add(ROOT / rel, arcname=rel.replace("\\", "/"))
            print("pack", rel)
    script = ROOT / "_remote_inject_dive_both.py"
    script.write_text(REMOTE_PY.replace("\r\n", "\n"), encoding="utf-8")
    run(["scp", "-o", "BatchMode=yes", str(tar_path), f"{HOST}:/tmp/dive_both_ship.tar"])
    run(["scp", "-o", "BatchMode=yes", str(script), f"{HOST}:{REMOTE_SCRIPT}"])
    run(
        [
            "ssh",
            "-o",
            "BatchMode=yes",
            HOST,
            f"rm -rf {REMOTE_WORK} && mkdir -p {REMOTE_WORK} && "
            f"tar -xf /tmp/dive_both_ship.tar -C {REMOTE_WORK} && "
            f"sed -i 's/\\r$//' {REMOTE_SCRIPT} && python3 {REMOTE_SCRIPT}",
        ]
    )
    print("LOCAL_DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
