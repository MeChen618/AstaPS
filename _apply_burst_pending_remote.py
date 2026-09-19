#!/usr/bin/env python3
"""Compile EnergyManager + ActionAvatarSkillStart, javassist-patch AbilityManager + Player, inject into remote jar."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

JAR = Path("/www/wwwroot/LunaGC-src-run.jar")
BASE = Path("/www/wwwroot")
SRC = Path("/tmp/burst_pending_src/src/main/java")
OUT = Path("/tmp/burst_pending_classes")
LOMBOK = Path("/tmp/lombok-edge.jar")
JAVASSIST = Path("/tmp/javassist.jar")
JAVAC = Path("/usr/lib/jvm/jdk-26/bin/javac")
JAVA = Path("/usr/lib/jvm/jdk-26/bin/java")
JAVAP = Path("/usr/lib/jvm/jdk-26/bin/javap")
PATCH_DIR = Path("/tmp/burst_pending_patch")
TS = time.strftime("%Y%m%d%H%M%S")
BAK = BASE / f"LunaGC-src-run.jar.bak-before-burst-pending-{TS}"


def run(cmd, **kwargs):
    print("+", " ".join(str(c) for c in cmd))
    return subprocess.run(cmd, check=False, text=True, capture_output=True, **kwargs)


def main() -> int:
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    if PATCH_DIR.exists():
        shutil.rmtree(PATCH_DIR)
    PATCH_DIR.mkdir(parents=True)

    sources = [
        SRC / "emu/grasscutter/game/managers/energy/EnergyManager.java",
        SRC / "emu/grasscutter/game/ability/actions/ActionAvatarSkillStart.java",
    ]
    for s in sources:
        if not s.exists():
            print("missing", s)
            return 1

    print("[1] compile EnergyManager + ActionAvatarSkillStart")
    r = run(
        [
            str(JAVAC),
            "-encoding",
            "UTF-8",
            "-cp",
            f"{JAR}:{LOMBOK}",
            "-processorpath",
            str(LOMBOK),
            "-d",
            str(OUT),
            *[str(s) for s in sources],
        ]
    )
    sys.stdout.write(r.stdout or "")
    sys.stderr.write(r.stderr or "")
    if r.returncode != 0:
        print("javac failed", r.returncode)
        return r.returncode

    classes = sorted(p for p in OUT.rglob("*.class"))
    print("[2] compiled", len(classes), "classes")
    for c in classes:
        print(" ", c.relative_to(OUT))

    print("[3] write javassist patcher")
    patcher = PATCH_DIR / "BurstPendingPatch.java"
    patcher.write_text(
        r'''
import javassist.*;
import java.io.File;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.io.*;
import java.util.*;

public class BurstPendingPatch {
    public static void main(String[] args) throws Exception {
        String jarPath = args[0];
        String outDir = args[1];
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jarPath);
        pool.insertClassPath(outDir); // EnergyManager with confirmBurstCast

        // AbilityManager: rewrite onPossibleElementalBurst (no lambdas — javassist limitation)
        // $1=ability, $2=modifier, $3=entityId
        CtClass am = pool.get("emu.grasscutter.game.ability.AbilityManager");
        CtMethod burst = am.getDeclaredMethod("onPossibleElementalBurst");
        burst.setBody(
            "{"
          + "  if ($1 == null) {"
          + "    emu.grasscutter.Grasscutter.getLogger().trace(\"possible elemental burst is null\");"
          + "    return;"
          + "  }"
          + "  if (this.burstCasterId == 0) return;"
          + "  boolean skillInvincibility ="
          + "      $2.state == emu.grasscutter.data.binout.AbilityModifier.State.Invincible;"
          + "  if ($2.onAdded != null) {"
          + "    for (int __i = 0; __i < $2.onAdded.length; __i++) {"
          + "      emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction __a = $2.onAdded[__i];"
          + "      if (__a != null"
          + "          && __a.type == emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction.Type.AttachAbilityStateResistance"
          + "          && __a.resistanceListID == 11002) {"
          + "        skillInvincibility = true;"
          + "        break;"
          + "      }"
          + "    }"
          + "  }"
          + "  if (this.burstCasterId == $3"
          + "      && ($1.getAvatarSkillStartIds().contains(java.lang.Integer.valueOf(this.burstSkillId)) || skillInvincibility)) {"
          + "    emu.grasscutter.Grasscutter.getLogger().trace("
          + "        \"Caster ID's {} burst successful, setting invulnerability\", java.lang.Integer.valueOf($3));"
          + "    this.abilityInvulnerable = true;"
          + "    int confirmedSkillId = this.burstSkillId;"
          + "    this.removePendingEnergyClear();"
          + "    try {"
          + "      emu.grasscutter.game.entity.GameEntity entity ="
          + "          this.player.getScene().getEntityById($3);"
          + "      if (entity instanceof emu.grasscutter.game.entity.EntityAvatar) {"
          + "        emu.grasscutter.game.entity.EntityAvatar entityAvatar ="
          + "            (emu.grasscutter.game.entity.EntityAvatar) entity;"
          + "        this.player.getEnergyManager()"
          + "            .confirmBurstCast(entityAvatar.getAvatar(), confirmedSkillId);"
          + "      }"
          + "    } catch (Throwable ignored) {}"
          + "  }"
          + "}"
        );
        am.writeFile(outDir);
        am.detach();

        // Player.onTick: expire pending energy after ArlecchinoBurstBoL.onTick
        CtClass player = pool.get("emu.grasscutter.game.player.Player");
        CtMethod onTick = player.getDeclaredMethod("onTick");
        onTick.instrument(new javassist.expr.ExprEditor() {
            public void edit(javassist.expr.MethodCall m) throws CannotCompileException {
                if ("emu.grasscutter.game.ability.ArlecchinoBurstBoL".equals(m.getClassName())
                        && "onTick".equals(m.getMethodName())) {
                    m.replace(
                        "{"
                      + "  $_ = $proceed($$);"
                      + "  try { this.getEnergyManager().onTick(); }"
                      + "  catch (Throwable __ignored) {}"
                      + "}"
                    );
                }
            }
        });
        player.writeFile(outDir);
        player.detach();

        System.out.println("javassist patch ok");
    }
}
'''.lstrip(),
        encoding="utf-8",
    )

    print("[4] compile patcher")
    r = run(
        [
            str(JAVAC),
            "-encoding",
            "UTF-8",
            "-cp",
            str(JAVASSIST),
            "-d",
            str(PATCH_DIR),
            str(patcher),
        ]
    )
    sys.stdout.write(r.stdout or "")
    sys.stderr.write(r.stderr or "")
    if r.returncode != 0:
        print("patcher javac failed")
        return r.returncode

    print("[5] run javassist patcher")
    r = run(
        [
            str(JAVA),
            "-cp",
            f"{PATCH_DIR}:{JAVASSIST}:{JAR}:{OUT}",
            "BurstPendingPatch",
            str(JAR),
            str(OUT),
        ]
    )
    sys.stdout.write(r.stdout or "")
    sys.stderr.write(r.stderr or "")
    if r.returncode != 0:
        print("javassist failed")
        return r.returncode

    print("[6] verify symbols")
    r = run([str(JAVAP), "-classpath", str(OUT), "-p", "emu.grasscutter.game.managers.energy.EnergyManager"])
    for line in (r.stdout or "").splitlines():
        if any(k in line for k in ("confirmBurstCast", "pendingBurst", "onTick", "expirePending")):
            print(" ", line.strip())

    print("[7] backup", BAK)
    shutil.copy2(JAR, BAK)

    print("[8] inject into jar")
    # jar uf from OUT
    class_args = []
    for c in sorted(OUT.rglob("*.class")):
        rel = c.relative_to(OUT).as_posix()
        class_args.append(rel)
        print("  inject", rel)
    r = run(["jar", "uf", str(JAR), *class_args], cwd=str(OUT))
    sys.stdout.write(r.stdout or "")
    sys.stderr.write(r.stderr or "")
    if r.returncode != 0:
        print("jar uf failed")
        return r.returncode

    print("[9] verify patched methods in jar")
    r = run([str(JAVAP), "-classpath", str(JAR), "-c", "-p", "emu.grasscutter.game.ability.AbilityManager"])
    am_txt = r.stdout or ""
    if "confirmBurstCast" in am_txt:
        print("  AbilityManager -> confirmBurstCast OK")
    else:
        print("  WARN: AbilityManager missing confirmBurstCast in disassembly")
        # still show snippet around removePendingEnergyClear
        for i, line in enumerate(am_txt.splitlines()):
            if "removePendingEnergyClear" in line:
                print("\n".join(am_txt.splitlines()[max(0, i - 5) : i + 8]))
                break

    r = run([str(JAVAP), "-classpath", str(JAR), "-c", "-p", "emu.grasscutter.game.player.Player"])
    if "getEnergyManager" in (r.stdout or "") and "EnergyManager.onTick" in (r.stdout or "").replace("/", "."):
        print("  Player.onTick energy expire OK")
    else:
        # looser check
        pt = r.stdout or ""
        idx = pt.find("ArlecchinoBurstBoL.onTick")
        if idx >= 0:
            snippet = pt[idx : idx + 400]
            print("  Player snippet:", snippet.replace("\n", " | "))
            if "EnergyManager" in snippet and "onTick" in snippet:
                print("  Player.onTick energy expire OK")
            else:
                print("  WARN: Player.onTick may lack energy expire")

    print("[10] restart")
    r = run(["bash", "/www/wwwroot/start-lunagc-src.sh"])
    sys.stdout.write(r.stdout or "")
    sys.stderr.write(r.stderr or "")
    time.sleep(3)
    r = run(["bash", "-lc", "pgrep -af 'LunaGC-src-run.jar' | head -5; ss -lntp | grep -E '22101|10443' || true"])
    sys.stdout.write(r.stdout or "")
    print("DONE", TS, "bak=", BAK)
    return 0


if __name__ == "__main__":
    sys.exit(main())
