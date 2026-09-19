#!/usr/bin/env python3
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
