# -*- coding: utf-8 -*-
"""Compile ElementFlora (烈焰花/冰雾花/绯樱绣球) unlock fix and inject into LunaGC-7.0.0.jar."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import zipfile
from datetime import datetime
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
JAR = ROOT / "LunaGC-7.0.0.jar"
OUT = ROOT / "_flora_classes"
LOMBOK = ROOT / "lombok-1.18.30.jar"
ERR_LOG = ROOT / "_flora_javac_err.txt"
JAVA = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe")

files = [
    # flora unlock core (ScriptLib skipped: jar/source TowerManager API mismatch)
    "src/main/java/emu/grasscutter/game/entity/gadget/GatherInteractHelper.java",
    "src/main/java/emu/grasscutter/game/entity/gadget/OreMiningHelper.java",
    "src/main/java/emu/grasscutter/game/entity/EntityBaseGadget.java",
    "src/main/java/emu/grasscutter/game/entity/EntityGadget.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketGadgetStateNotify.java",
    # jar EnvironmentalSealHelper lacks tryProcessAttack used by EntityGadget/OreMining
    "src/main/java/emu/grasscutter/game/entity/EnvironmentalSealHelper.java",
]


def main() -> int:
    if not JAVA.exists():
        ERR_LOG.write_text("javac not found\n", encoding="utf-8")
        return 1
    if not JAR.exists():
        ERR_LOG.write_text(f"jar not found: {JAR}\n", encoding="utf-8")
        return 1
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    cp_parts = [str(JAR)]
    if LOMBOK.exists():
        cp_parts.insert(0, str(LOMBOK))
    cp = os.pathsep.join(cp_parts)

    srcs = [str(ROOT / f) for f in files]
    missing = [s for s in srcs if not Path(s).exists()]
    if missing:
        ERR_LOG.write_text("missing:\n" + "\n".join(missing), encoding="utf-8")
        return 1

    cmd = [
        str(JAVA),
        "--release",
        "17",
        "-encoding",
        "UTF-8",
        "-cp",
        cp,
        "-d",
        str(OUT),
    ]
    if LOMBOK.exists():
        cmd.extend(["-processorpath", str(LOMBOK)])
    cmd.extend(srcs)

    r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True)
    err = (r.stderr or b"").decode("utf-8", "replace")
    out = (r.stdout or b"").decode("utf-8", "replace")
    ERR_LOG.write_text(f"return={r.returncode}\n{err}\n{out}", encoding="utf-8")
    if r.returncode != 0:
        sys.stderr.buffer.write(b"javac failed; see _flora_javac_err.txt\n")
        return r.returncode

    adds = {}
    for root, _, fs in os.walk(OUT):
        for f in fs:
            if f.endswith(".class"):
                full = Path(root) / f
                adds[full.relative_to(OUT).as_posix()] = full

    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    bak = ROOT / f"LunaGC-7.0.0.jar.bak-before-flora-{stamp}"
    shutil.copy2(JAR, bak)

    tmp = ROOT / "LunaGC-7.0.0.jar.tmp-flora"
    if tmp.exists():
        tmp.unlink()
    seen = set()
    with zipfile.ZipFile(JAR, "r") as zin, zipfile.ZipFile(
        tmp, "w", compression=zipfile.ZIP_DEFLATED
    ) as zout:
        skip = set(adds)
        for info in zin.infolist():
            n = info.filename.replace("\\", "/")
            if n in skip or n in seen:
                continue
            seen.add(n)
            zout.writestr(info, zin.read(info.filename))
        for rel, full in adds.items():
            zout.write(full, rel)
    os.replace(tmp, JAR)

    summary = [
        f"bak={bak.name}",
        f"classes={len(adds)}",
    ]
    summary.extend("  " + rel for rel in sorted(adds))
    (ROOT / "_flora_inject_ok.txt").write_text("\n".join(summary), encoding="utf-8")
    sys.stderr.buffer.write(f"inject ok classes={len(adds)} bak={bak.name}\n".encode())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
