# -*- coding: utf-8 -*-
"""Compile + inject Fontaine ShareCD fix into remote LunaGC-src-run.jar and restart."""
from __future__ import annotations

import subprocess
import tarfile
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
HOST = "qianzhi-211"
REMOTE_WORK = "/tmp/sharecd_build"
REMOTE_SCRIPT = "/tmp/_remote_build_inject_sharecd.py"

SRCS = [
    "src/main/java/emu/grasscutter/game/ability/ShareCDHelper.java",
    "src/main/java/emu/grasscutter/game/ability/actions/ActionAvatarShareCDSkillStart.java",
    "src/main/java/emu/grasscutter/game/ability/actions/ActionAddAvatarSkillInfo.java",
    "src/main/java/emu/grasscutter/game/ability/actions/ActionRemoveAvatarSkillInfo.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketAllShareCDDataNotify.java",
]

REMOTE_PY = r'''#!/usr/bin/env python3
import os, shutil, subprocess, time, zipfile
from pathlib import Path

JAR = Path("/www/wwwroot/LunaGC-src-run.jar")
WORK = Path("/tmp/sharecd_build")
SRC = WORK / "src"
OUT = WORK / "classes"
TS = time.strftime("%Y%m%d%H%M%S")
BAK = Path(f"/www/wwwroot/LunaGC-src-run.jar.bak-before-sharecd-{TS}")
TMP = Path("/www/wwwroot/LunaGC-src-run.jar.tmp-sharecd")

WANT = [
    "emu/grasscutter/game/ability/ShareCDHelper.class",
    "emu/grasscutter/game/ability/actions/ActionAvatarShareCDSkillStart.class",
    "emu/grasscutter/game/ability/actions/ActionAddAvatarSkillInfo.class",
    "emu/grasscutter/game/ability/actions/ActionRemoveAvatarSkillInfo.class",
    "emu/grasscutter/server/packet/send/PacketAllShareCDDataNotify.class",
]

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
    java_files = sorted(SRC.rglob("*.java"))
    assert java_files, "no sources"
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    javac = find_javac()
    cmd = [javac, "-encoding", "UTF-8", "-cp", str(JAR), "-d", str(OUT)] + [str(p) for p in java_files]
    print("+", " ".join(cmd[:6]), f"... ({len(java_files)} files)")
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr)
        raise SystemExit(r.returncode)

    adds = {}
    for rel in WANT:
        cls = OUT / rel
        assert cls.exists(), cls
        adds[rel] = cls
        data = cls.read_bytes()
        print("built", rel, len(data))

    helper = (OUT / "emu/grasscutter/game/ability/ShareCDHelper.class").read_bytes()
    assert b"AllShareCDDataNotify" in helper or b"PacketAllShareCDDataNotify" in helper
    assert b"startShareCd" in helper

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

    z = zipfile.ZipFile(JAR)
    for rel in WANT:
        assert rel in z.namelist(), rel
    print("VERIFY_OK", JAR.stat().st_size)

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
        stdout=open("/www/wwwroot/logs/sharecd-restart.log", "ab"),
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    time.sleep(14)
    subprocess.call(["bash", "-lc", "pgrep -af 'java.*LunaGC-src-run.jar' | head -3 || echo NO_JAVA"])
    # confirm boot registered ShareCD handlers / loaded excel
    time.sleep(6)
    subprocess.call([
        "bash", "-lc",
        "grep -E 'ShareCD|AvatarShareCDSkillStart|AddAvatarSkillInfo' /www/wwwroot/logs/latest.log 2>/dev/null | tail -20 || true"
    ])
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
    for rel in SRCS:
        if not (ROOT / rel).exists():
            raise SystemExit(f"missing {rel}")

    tar_path = ROOT / "_sharecd_ship.tar"
    if tar_path.exists():
        tar_path.unlink()
    with tarfile.open(tar_path, "w") as tar:
        for rel in SRCS:
            tar.add(ROOT / rel, arcname=rel.replace("\\", "/"))
            print("pack", rel)

    script = ROOT / "_remote_build_inject_sharecd.py"
    script.write_text(REMOTE_PY.replace("\r\n", "\n"), encoding="utf-8")

    run(["scp", "-o", "BatchMode=yes", str(tar_path), f"{HOST}:/tmp/sharecd_ship.tar"])
    run(["scp", "-o", "BatchMode=yes", str(script), f"{HOST}:{REMOTE_SCRIPT}"])
    run(
        [
            "ssh",
            "-o",
            "BatchMode=yes",
            HOST,
            f"rm -rf {REMOTE_WORK} && mkdir -p {REMOTE_WORK} && "
            f"tar -xf /tmp/sharecd_ship.tar -C {REMOTE_WORK} && "
            f"sed -i 's/\\r$//' {REMOTE_SCRIPT} && python3 {REMOTE_SCRIPT}",
        ]
    )
    print("LOCAL_DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
