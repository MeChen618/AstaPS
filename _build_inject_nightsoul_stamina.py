# -*- coding: utf-8 -*-
"""Compile nightsoul stamina exempt fix and patch LunaGC-7.0.0.jar.

Does NOT replace AbilityModifier (enum/API drift risk). Relies on IsNyxState /
NyxCost marks / NyxInstant+NyxValue / NyxState modifiers.
"""
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
JAR = ROOT / "LunaGC-7.0.0.jar"
BAK = ROOT / "LunaGC-7.0.0.jar.bak-before-nightsoul-stamina"
OUT = ROOT / "_nightsoul_stamina_classes"
SHIP = ROOT / "_nightsoul_stamina_ship"
JAVA = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe")
LOMBOK = ROOT / "lombok.jar"
ERR_LOG = ROOT / "_nightsoul_stamina_javac_err.txt"

files = [
    "src/main/java/emu/grasscutter/game/ability/NightsoulStaminaExempt.java",
    "src/main/java/emu/grasscutter/game/managers/stamina/StaminaManager.java",
    "src/main/java/emu/grasscutter/game/ability/mixins/CostStaminaMixin.java",
    "src/main/java/emu/grasscutter/game/ability/mixins/NyxCostMixin.java",
    "src/main/java/emu/grasscutter/game/ability/actions/ActionChangePlayMode.java",
    "src/main/java/emu/grasscutter/game/ability/actions/ActionNyxAdd.java",
    "src/main/java/emu/grasscutter/game/ability/actions/ActionSetGlobalValue.java",
    "src/main/java/emu/grasscutter/game/ability/NyxHelper.java",
]

keep_prefix = (
    "emu/grasscutter/game/ability/NightsoulStaminaExempt",
    "emu/grasscutter/game/managers/stamina/StaminaManager",
    "emu/grasscutter/game/ability/mixins/CostStaminaMixin",
    "emu/grasscutter/game/ability/mixins/NyxCostMixin",
    "emu/grasscutter/game/ability/actions/ActionChangePlayMode",
    "emu/grasscutter/game/ability/actions/ActionNyxAdd",
    "emu/grasscutter/game/ability/actions/ActionSetGlobalValue",
    "emu/grasscutter/game/ability/NyxHelper",
)

# Restore AbilityModifier* from pre-patch backup if present in current jar from earlier inject.
restore_prefix = "emu/grasscutter/data/binout/AbilityModifier"

if OUT.exists():
    shutil.rmtree(OUT)
OUT.mkdir(parents=True)

cp = os.pathsep.join([str(JAR), str(LOMBOK)])
cmd = [
    str(JAVA),
    "--release",
    "17",
    "-encoding",
    "UTF-8",
    "-cp",
    cp,
    "-processorpath",
    str(LOMBOK),
    "-d",
    str(OUT),
] + [str(ROOT / f) for f in files]
r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True)
err = (r.stderr or b"").decode("utf-8", "replace")
ERR_LOG.write_text(err, encoding="utf-8")
print("javac", r.returncode)
if r.returncode != 0:
    sys.stderr.buffer.write(b"javac failed; see _nightsoul_stamina_javac_err.txt\n")
    sys.exit(r.returncode)

if SHIP.exists():
    shutil.rmtree(SHIP)
SHIP.mkdir(parents=True)

for root, _, fs in os.walk(OUT):
    for f in fs:
        if not f.endswith(".class"):
            continue
        full = Path(root) / f
        rel = full.relative_to(OUT).as_posix()
        if not any(rel.startswith(p) for p in keep_prefix):
            continue
        dest = SHIP / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(full, dest)
        print("ship", rel)

adds = {p.relative_to(SHIP).as_posix(): p for p in SHIP.rglob("*.class")}
restores = {}
if BAK.exists():
    with zipfile.ZipFile(BAK, "r") as zin:
        for name in zin.namelist():
            if name.startswith(restore_prefix) and name.endswith(".class"):
                restores[name] = zin.read(name)
    print("restore AbilityModifier entries", len(restores))

if not BAK.exists():
    bak = ROOT / "LunaGC-7.0.0.jar.bak-before-nightsoul-stamina"
    # already defined
    pass
if not BAK.exists():
    shutil.copy2(JAR, BAK)
    print("backup", BAK.name)

tmp = ROOT / "LunaGC-7.0.0.jar.tmp-nightsoul-stamina"
seen = set()
with zipfile.ZipFile(JAR, "r") as zin, zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        name = info.filename
        if name in restores:
            zout.writestr(info, restores[name])
            seen.add(name)
        elif name in adds:
            with open(adds[name], "rb") as fh:
                zout.writestr(info, fh.read())
            seen.add(name)
        else:
            zout.writestr(info, zin.read(name))
    for name, path in adds.items():
        if name in seen:
            continue
        with open(path, "rb") as fh:
            zout.writestr(name, fh.read())
        print("added", name)

# Stop holders before replace attempted by caller; still try here.
try:
    os.replace(tmp, JAR)
    print("patched", JAR.name, "classes", len(adds), "restored", len(restores))
except PermissionError:
    print("TMP_READY", tmp)
    sys.exit(3)
