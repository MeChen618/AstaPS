# -*- coding: utf-8 -*-
"""Compile ReliquaryDust classes and inject into LunaGC-7.0.0.jar."""
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
JAR = ROOT / "LunaGC-7.0.0.jar"
OUT = ROOT / "_dust_classes"
JAVA = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe")

files = [
    "src/main/java/emu/grasscutter/utils/ProtoWire.java",
    "src/main/java/emu/grasscutter/game/systems/ReliquaryDustSystem.java",
    "src/main/java/emu/grasscutter/game/systems/ArtifactTransmuterSystem.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketReliquaryDustRsp.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketReliquaryDustCompanionRsp.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketReliquaryDustConfirmRsp.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketReliquaryDustSelectRsp.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketReliquaryDustDataNotify.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketReliquaryOfferCompanionNotify.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerReliquaryDustReq.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerReliquaryDustCompanionReq.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerReliquaryDustSelectReq.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerReliquaryDustConfirmReqA.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerReliquaryDustConfirmReqB.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerUnionCmdNotify.java",
    "src/main/java/emu/grasscutter/server/game/GameServerPacketHandler.java",
    "src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java",
]

# Stop if jar locked
print("compiling...")
if OUT.exists():
    shutil.rmtree(OUT)
OUT.mkdir(parents=True)

srcs = [str(ROOT / f) for f in files]
cmd = [
    str(JAVA),
    "--release",
    "17",
    "-encoding",
    "UTF-8",
    "-cp",
    str(JAR),
    "-d",
    str(OUT),
] + srcs
r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True)
err = r.stderr.decode("utf-8", "replace")
(ROOT / "_dust_javac_err.txt").write_text(err, encoding="utf-8")
print("javac return", r.returncode)
if r.returncode != 0:
    try:
        print(err[-3000:])
    except Exception:
        (ROOT / "_dust_javac_err.txt").write_text(err, encoding="utf-8")
        print("see _dust_javac_err.txt")
    sys.exit(r.returncode)

adds = {}
for root, _, fs in os.walk(OUT):
    for f in fs:
        if f.endswith(".class"):
            full = Path(root) / f
            adds[full.relative_to(OUT).as_posix()] = full
print("classes", len(adds))
for rel in sorted(adds):
    print(" ", rel)

# Backup the currently-running user jar once per name
bak = ROOT / "LunaGC-7.0.0.jar.bak-before-dust-reinject"
if not bak.exists():
    shutil.copy2(JAR, bak)
    print("backup", bak)

tmp = ROOT / "LunaGC-7.0.0.jar.tmp-dust"
if tmp.exists():
    tmp.unlink()
seen = set()
dupes = 0
with zipfile.ZipFile(JAR, "r") as zin, zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED) as zout:
    skip = set(adds)
    for info in zin.infolist():
        n = info.filename.replace("\\", "/")
        if n in skip or n in seen:
            if n in seen:
                dupes += 1
            continue
        seen.add(n)
        zout.writestr(info, zin.read(info.filename))
    for rel, full in adds.items():
        zout.write(full, rel)
os.replace(tmp, JAR)
print("updated", JAR, JAR.stat().st_size, "skipped_dupes", dupes)
