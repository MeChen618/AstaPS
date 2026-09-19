#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

jar = "/www/wwwroot/LunaGC-src-run.jar"
javap = "/usr/lib/jvm/jdk-26/bin/javap"
out = Path("/tmp/am.javap.txt")
subprocess.check_call([javap, "-classpath", jar, "-c", "-p", "emu.grasscutter.game.ability.AbilityManager"], stdout=out.open("w", encoding="utf-8"))
text = out.read_text(encoding="utf-8", errors="replace").splitlines()
for i, line in enumerate(text):
    if "onPossibleElementalBurst" in line:
        for j in range(i, min(i + 160, len(text))):
            print(text[j])
            if j > i + 8 and text[j].startswith("  ") and not text[j].startswith("   "):
                # next method signature-ish
                s = text[j].strip()
                if s.startswith(("public ", "private ", "protected ", "static ")):
                    break
        break
else:
    print("NOT FOUND", file=sys.stderr)
    sys.exit(1)

print("\n==== Player.onTick head ====")
out2 = Path("/tmp/player.javap.txt")
subprocess.check_call([javap, "-classpath", jar, "-c", "-p", "emu.grasscutter.game.player.Player"], stdout=out2.open("w", encoding="utf-8"))
text = out2.read_text(encoding="utf-8", errors="replace").splitlines()
for i, line in enumerate(text):
    if "void onTick()" in line:
        for j in range(i, min(i + 100, len(text))):
            print(text[j])
            if j > i + 8 and text[j].startswith("  ") and not text[j].startswith("   "):
                s = text[j].strip()
                if s.startswith(("public ", "private ", "protected ", "static ")):
                    break
        break
