# -*- coding: utf-8 -*-
"""Compile Sacred Sakura (神樱) Offering fix and inject into LunaGC-7.0.0.jar, then restart."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import time
import zipfile
from datetime import datetime
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
JAR = ROOT / "LunaGC-7.0.0.jar"
OUT = ROOT / "_offering_classes"
LOMBOK = ROOT / "lombok-1.18.30.jar"
ERR_LOG = ROOT / "_offering_javac_err.txt"
JAVA = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe")

files = [
    "src/main/java/emu/grasscutter/game/entity/gadget/OfferingHelper.java",
    "src/main/java/emu/grasscutter/game/entity/gadget/GadgetOffering.java",
    "src/main/java/emu/grasscutter/game/world/OfferingSpawnHelper.java",
    "src/main/java/emu/grasscutter/game/entity/EntityGadget.java",
    "src/main/java/emu/grasscutter/game/world/Scene.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerPlayerOfferingReq.java",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerTakeOfferingLevelRewardReq.java",
    "src/main/java/emu/grasscutter/server/game/GameServerPacketHandler.java",
    "src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java",
]


def find_java_pid() -> list[int]:
    try:
        out = subprocess.check_output(
            ["wmic", "process", "where", "name='java.exe'", "get", "ProcessId,CommandLine", "/FORMAT:LIST"],
            cwd=str(ROOT),
            stderr=subprocess.DEVNULL,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except Exception:
        return []
    pids = []
    cur_cmd = ""
    cur_pid = None
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("CommandLine="):
            cur_cmd = line[len("CommandLine=") :]
        elif line.startswith("ProcessId="):
            try:
                cur_pid = int(line[len("ProcessId=") :])
            except ValueError:
                cur_pid = None
            if cur_pid and ("LunaGC" in cur_cmd or "grasscutter" in cur_cmd.lower() or "Chiori" in cur_cmd):
                pids.append(cur_pid)
            cur_cmd = ""
            cur_pid = None
    return pids


def stop_server() -> None:
    pids = find_java_pid()
    for pid in pids:
        print("kill", pid)
        subprocess.run(["taskkill", "/PID", str(pid), "/F"], capture_output=True)
    # wait until jar unlocked
    for _ in range(30):
        if not pids:
            break
        time.sleep(0.5)
        pids = find_java_pid()
    time.sleep(1.0)


def start_server() -> None:
    # Prefer existing start scripts
    candidates = [
        ROOT / "start.bat",
        ROOT / "start-local.bat",
        ROOT / "run.bat",
    ]
    for bat in candidates:
        if bat.exists():
            print("start", bat.name)
            subprocess.Popen(
                ["cmd", "/c", str(bat)],
                cwd=str(ROOT),
                creationflags=subprocess.CREATE_NEW_CONSOLE if hasattr(subprocess, "CREATE_NEW_CONSOLE") else 0,
            )
            return
    # Fallback: java -jar
    print("start java -jar", JAR.name)
    subprocess.Popen(
        ["java", "-jar", str(JAR)],
        cwd=str(ROOT),
        creationflags=subprocess.CREATE_NEW_CONSOLE if hasattr(subprocess, "CREATE_NEW_CONSOLE") else 0,
    )


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

    print("javac...")
    r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True)
    err = (r.stderr or b"").decode("utf-8", "replace")
    out = (r.stdout or b"").decode("utf-8", "replace")
    ERR_LOG.write_text(f"return={r.returncode}\n{err}\n{out}", encoding="utf-8")
    if r.returncode != 0:
        sys.stderr.buffer.write(b"javac failed; see _offering_javac_err.txt\n")
        return r.returncode

    adds = {}
    for root, _, fs in os.walk(OUT):
        for f in fs:
            if f.endswith(".class"):
                full = Path(root) / f
                adds[full.relative_to(OUT).as_posix()] = full
    print("classes", len(adds))

    print("stop server...")
    stop_server()

    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    bak = ROOT / f"LunaGC-7.0.0.jar.bak-before-offering-{stamp}"
    shutil.copy2(JAR, bak)
    print("backup", bak.name)

    tmp = ROOT / "LunaGC-7.0.0.jar.tmp-offering"
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
            print("ship", rel)

    # replace with retries (Windows file lock)
    for i in range(20):
        try:
            os.replace(tmp, JAR)
            break
        except PermissionError:
            print("jar locked, retry", i)
            stop_server()
            time.sleep(1.0)
    else:
        alt = ROOT / f"LunaGC-7.0.0.injected-offering-{stamp}.jar"
        shutil.copy2(tmp, alt)
        print("os.replace failed; wrote", alt.name)
        return 2

    print("local jar patched", JAR.stat().st_size)
    start_server()
    print("done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
