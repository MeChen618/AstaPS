# -*- coding: utf-8 -*-
"""Javassist-patch ScriptLib.SetGadgetEnableInteract for ElementFlora fallback."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(r"D:\7.0织锦\Chiori")
JAR = ROOT / "LunaGC-7.0.0.jar"
JAVA = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\java.exe")
JAVAC = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe")
PATCH_SRC = ROOT / "_PatchScriptLibFlora.java"
PATCH_CLS = ROOT / "_flora_scriptlib_patch"
OUT_CLASS = ROOT / "_flora_scriptlib_out"


BODY = r"""
{
    org.slf4j.Logger __log = emu.grasscutter.scripts.ScriptLib.logger;
    __log.debug("[LUA] Call SetGadgetEnableInteract with {} {} {}", new Object[]{ Integer.valueOf($1), Integer.valueOf($2), Boolean.valueOf($3) });
    emu.grasscutter.game.entity.GameEntity entity =
        this.getSceneScriptManager().getScene().getEntityByConfigId($2, $1);
    if (!(entity instanceof emu.grasscutter.game.entity.EntityGadget)) {
        entity = this.getCurrentEntityGadget();
    }
    if (!(entity instanceof emu.grasscutter.game.entity.EntityGadget)) {
        __log.warn("SetGadgetEnableInteract: no gadget group={} config={} enable={}",
            new Object[]{ Integer.valueOf($1), Integer.valueOf($2), Boolean.valueOf($3) });
        return -1;
    }
    emu.grasscutter.game.entity.EntityGadget gadget = (emu.grasscutter.game.entity.EntityGadget) entity;
    if ($3) {
        try {
            emu.grasscutter.game.entity.gadget.GatherInteractHelper.onGatherReady(gadget);
        } catch (Throwable t) {}
        if (!gadget.isInteractEnabled()) {
            gadget.setInteractEnabled(true);
        }
    } else {
        try {
            emu.grasscutter.game.entity.gadget.GatherInteractHelper.markGatherInteractDisabled(gadget);
        } catch (Throwable t) {}
        gadget.setInteractEnabled(false);
    }
    return 0;
}
"""


def main() -> int:
    PATCH_CLS.mkdir(parents=True, exist_ok=True)
    OUT_CLASS.mkdir(parents=True, exist_ok=True)

    src = f"""
import javassist.*;
import java.io.File;

public class _PatchScriptLibFlora {{
    public static void main(String[] args) throws Exception {{
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(args[0]);
        CtClass ct = pool.get("emu.grasscutter.scripts.ScriptLib");
        CtMethod m = ct.getDeclaredMethod("SetGadgetEnableInteract");
        m.setBody({BODY!r});
        ct.writeFile(args[1]);
        System.out.println("ScriptLib.SetGadgetEnableInteract patched");
    }}
}}
"""
    # Fix the body embedding - use text block carefully
    src = """
import javassist.*;

public class _PatchScriptLibFlora {
    public static void main(String[] args) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(args[0]);
        CtClass ct = pool.get("emu.grasscutter.scripts.ScriptLib");
        CtMethod m = ct.getDeclaredMethod("SetGadgetEnableInteract");
        m.setBody(
            "{"
            + "org.slf4j.Logger __log = emu.grasscutter.scripts.ScriptLib.logger;"
            + "__log.debug(\\"[LUA] Call SetGadgetEnableInteract with {} {} {}\\", new Object[]{ Integer.valueOf($1), Integer.valueOf($2), Boolean.valueOf($3) });"
            + "emu.grasscutter.game.entity.GameEntity entity = this.getSceneScriptManager().getScene().getEntityByConfigId($2, $1);"
            + "if (!(entity instanceof emu.grasscutter.game.entity.EntityGadget)) {"
            + "  entity = this.getCurrentEntityGadget();"
            + "}"
            + "if (!(entity instanceof emu.grasscutter.game.entity.EntityGadget)) {"
            + "  __log.warn(\\"SetGadgetEnableInteract: no gadget group={} config={} enable={}\\", new Object[]{ Integer.valueOf($1), Integer.valueOf($2), Boolean.valueOf($3) });"
            + "  return -1;"
            + "}"
            + "emu.grasscutter.game.entity.EntityGadget gadget = (emu.grasscutter.game.entity.EntityGadget) entity;"
            + "if ($3) {"
            + "  try { emu.grasscutter.game.entity.gadget.GatherInteractHelper.onGatherReady(gadget); } catch (Throwable t) {}"
            + "  if (!gadget.isInteractEnabled()) { gadget.setInteractEnabled(true); }"
            + "} else {"
            + "  try { emu.grasscutter.game.entity.gadget.GatherInteractHelper.markGatherInteractDisabled(gadget); } catch (Throwable t) {}"
            + "  gadget.setInteractEnabled(false);"
            + "}"
            + "return 0;"
            + "}"
        );
        ct.writeFile(args[1]);
        System.out.println("ScriptLib.SetGadgetEnableInteract patched");
    }
}
"""
    PATCH_SRC.write_text(src, encoding="utf-8")

    cp = str(JAR)
    r = subprocess.run(
        [str(JAVAC), "-encoding", "UTF-8", "-cp", cp, "-d", str(PATCH_CLS), str(PATCH_SRC)],
        cwd=str(ROOT),
        capture_output=True,
    )
    if r.returncode != 0:
        err = (r.stderr or b"").decode("utf-8", "replace")
        (ROOT / "_flora_scriptlib_javac_err.txt").write_text(err, encoding="utf-8")
        sys.stderr.buffer.write(b"patcher javac failed\n")
        return r.returncode

    r2 = subprocess.run(
        [
            str(JAVA),
            "-cp",
            os.pathsep.join([str(PATCH_CLS), cp]),
            "_PatchScriptLibFlora",
            str(JAR),
            str(OUT_CLASS),
        ],
        cwd=str(ROOT),
        capture_output=True,
    )
    out = ((r2.stdout or b"") + (r2.stderr or b"")).decode("utf-8", "replace")
    (ROOT / "_flora_scriptlib_patch_log.txt").write_text(out, encoding="utf-8")
    if r2.returncode != 0:
        sys.stderr.buffer.write(b"javassist patch failed\n")
        return r2.returncode

    patched = OUT_CLASS / "emu" / "grasscutter" / "scripts" / "ScriptLib.class"
    if not patched.exists():
        sys.stderr.buffer.write(b"patched class missing\n")
        return 1

    tmp = ROOT / "LunaGC-7.0.0.jar.tmp-flora-scriptlib"
    if tmp.exists():
        tmp.unlink()
    rel = "emu/grasscutter/scripts/ScriptLib.class"
    with zipfile.ZipFile(JAR, "r") as zin, zipfile.ZipFile(
        tmp, "w", compression=zipfile.ZIP_DEFLATED
    ) as zout:
        for info in zin.infolist():
            n = info.filename.replace("\\", "/")
            if n == rel:
                continue
            zout.writestr(info, zin.read(info.filename))
        zout.write(patched, rel)
    os.replace(tmp, JAR)
    sys.stderr.buffer.write(b"ScriptLib inject ok\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
