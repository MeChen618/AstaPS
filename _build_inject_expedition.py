# -*- coding: utf-8 -*-
"""Compile expedition dispatch/reward fixes and inject into LunaGC-7.0.0.jar."""
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
OUT = ROOT / "_expedition_classes"
LOMBOK = ROOT / "lombok-1.18.30.jar"
ERR_LOG = ROOT / "_expedition_javac_err.txt"
OK_LOG = ROOT / "_expedition_inject_ok.txt"
JAVA = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe")

files = [
    "src/main/java/emu/grasscutter/game/expedition/ExpeditionHelper.java",
    "src/main/java/emu/grasscutter/game/expedition/ExpeditionRewardData.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketAvatarExpeditionAllDataRsp.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketAvatarExpeditionStartRsp.java",
    "src/main/java/emu/grasscutter/server/packet/send/PacketAvatarExpeditionGetRewardRsp.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerAvatarExpeditionAllDataReq.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerAvatarExpeditionStartReq.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerAvatarExpeditionCallBackReq.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerAvatarExpeditionGetRewardReq.java",
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
        sys.stderr.buffer.write(b"javac failed; see _expedition_javac_err.txt\n")
        return r.returncode

    adds = {}
    for root, _, fs in os.walk(OUT):
        for f in fs:
            if f.endswith(".class"):
                full = Path(root) / f
                adds[full.relative_to(OUT).as_posix()] = full

    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    bak = ROOT / f"LunaGC-7.0.0.jar.bak-before-expedition-{stamp}"
    shutil.copy2(JAR, bak)

    tmp = ROOT / "LunaGC-7.0.0.jar.tmp-expedition"
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

    try:
        os.replace(tmp, JAR)
        replaced = str(JAR.name)
    except OSError as e:
        # Running java -jar locks the file on Windows (WinError 5).
        alt = ROOT / f"LunaGC-7.0.0.injected-expedition-{stamp}.jar"
        if tmp.exists():
            os.replace(tmp, alt)
        ERR_LOG.write_text(
            f"os.replace failed: {e}\nwrote {alt.name} instead; stop server and copy over LunaGC-7.0.0.jar\n",
            encoding="utf-8",
        )
        sys.stderr.buffer.write(
            f"jar locked; wrote {alt.name} (stop server then copy)\n".encode()
        )
        return 2

    summary = [
        f"bak={bak.name}",
        f"replaced={replaced}",
        f"classes={len(adds)}",
    ]
    summary.extend("  " + rel for rel in sorted(adds))
    OK_LOG.write_text("\n".join(summary), encoding="utf-8")
    sys.stderr.buffer.write(f"inject ok classes={len(adds)} bak={bak.name}\n".encode())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
