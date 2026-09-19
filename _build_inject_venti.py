# -*- coding: utf-8 -*-
"""Compile/inject Venti Hexenzirkel fixes into LunaGC-7.0.0.jar (jar-compatible).

Source tree has drifted from the running jar (EscoffierSkillCookProto, AbilityMaxHpRatioHelper,
Effigy reborn prism API, etc.). This script:
  1) compiles jar-compatible copies under _venti_build/
  2) injects those classes
  3) javassist-patches AbilityManager.addAbilityToEntity for talent specials on client gadgets
"""
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
BUILD = ROOT / "_venti_build"
OUT = ROOT / "_venti_classes"
PATCH_DIR = ROOT / "_venti_patch"
LOMBOK = ROOT / "lombok-1.18.30.jar"
ERR_LOG = ROOT / "_venti_javac_err.txt"
OK_LOG = ROOT / "_venti_inject_ok.txt"
JAVA = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\java.exe")
JAVAC = Path(r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe")
JAVASSIST = next(Path(r"C:\Users\lenovo\.gradle\caches").rglob("javassist-3.28.0-GA.jar"))

ACTION_CREATE_GADGET = r'''
package emu.grasscutter.game.ability.actions;

import com.google.protobuf.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.IneffaRelayHelper;
import emu.grasscutter.game.ability.VentiSkillObjHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.CampTargetType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityActionCreateGadgetOuterClass.AbilityActionCreateGadget;

@AbilityAction(AbilityModifierAction.Type.CreateGadget)
public class ActionCreateGadget extends AbilityActionHandler {

    private static boolean clientOwnsChain(GameEntity entity) {
        if (entity instanceof EntityClientGadget) return true;
        while (entity instanceof EntityGadget summon && summon.getOwner() != null) {
            entity = summon.getOwner();
            if (entity instanceof EntityAvatar || entity instanceof EntityClientGadget) return true;
        }
        return false;
    }

    private static boolean isEscoffierClientSkillObj(int gadgetId) {
        return gadgetId == 42112001 || gadgetId == 42112004 || gadgetId == 42112005;
    }

    private static boolean isMitakenarukamiClientSkillObj(int gadgetId) {
        return switch (gadgetId) {
            case 42906104, 42906110, 42906114, 42906116, 42906119, 42906120, 42906123, 42906125,
                    42906126, 42906127 -> true;
            default -> false;
        };
    }

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var entity = ability.getOwner();

        if (clientOwnsChain(entity)) {
            return true;
        }
        if (isEscoffierClientSkillObj(action.gadgetID)) {
            return true;
        }
        if (isMitakenarukamiClientSkillObj(action.gadgetID)) {
            return true;
        }
        if (emu.grasscutter.game.world.EffigyCombatHelper.isEffigyClientOwnedGadget(action.gadgetID)) {
            return true;
        }
        if (IneffaRelayHelper.isIneffaClientSkillObj(action.gadgetID)) {
            var player = ability.getPlayerOwner();
            if (player != null) {
                IneffaRelayHelper.purgeServerShells(player, false);
            }
            return true;
        }
        // Venti Stormeye / WindBlade / WindField: client-owned SkillObj.
        if (VentiSkillObjHelper.isVentiClientSkillObj(action.gadgetID)) {
            VentiSkillObjHelper.purgeServerShells(ability.getPlayerOwner());
            return true;
        }

        AbilityActionCreateGadget createGadget;
        try {
            createGadget = AbilityActionCreateGadget.parseFrom(abilityData);
        } catch (InvalidProtocolBufferException e) {
            return false;
        }

        var pos =
                createGadget.hasPos() ? new Position(createGadget.getPos()) : entity.getPosition().clone();
        var rot =
                createGadget.hasRot() ? new Position(createGadget.getRot()) : entity.getRotation().clone();

        var owner = action.ownerIsTarget ? target : entity;
        var entityCreated =
                new EntityGadget(
                        entity.getScene(),
                        action.gadgetID,
                        pos,
                        rot,
                        null,
                        action.campID,
                        CampTargetType.getTypeByName(action.campTargetType).getValue(),
                        false);
        entityCreated.setOwner(owner);
        entityCreated.initAbilities();

        entity.getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityGadget g
                        && g.getGadgetId() == action.gadgetID
                        && g.getOwner() == owner)
                .toList()
                .forEach(stale -> entity.getScene().removeEntity(stale));

        if (owner instanceof EntityGadget ownerGadget) {
            ownerGadget.getChildren().add(entityCreated);
        }

        entity.getScene().addEntity(entityCreated);

        Grasscutter.getLogger()
                .trace(
                        "Gadget {} created at pos {} rot {}",
                        action.gadgetID,
                        entityCreated.getPosition(),
                        entityCreated.getRotation());

        return true;
    }
}
'''

HANDLER_EVT = r'''
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.ability.IneffaRelayHelper;
import emu.grasscutter.game.ability.VentiSkillObjHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.EvtCreateGadgetNotify)
public class HandlerEvtCreateGadgetNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        EvtCreateGadgetNotify notify = EvtCreateGadgetNotify.parseFrom(payload);

        var scene = session.getPlayer().getScene();

        if (scene.getEntityById(notify.getEntityId()) != null) {
            return;
        }

        var gadgetId = notify.getConfigId();
        EntityClientGadget gadget =
                switch (gadgetId) {
                    case EntitySolarIsotomaClientGadget.GADGET_ID -> new EntitySolarIsotomaClientGadget(
                            session.getPlayer().getScene(), session.getPlayer(), notify);
                    default -> new EntityClientGadget(
                            session.getPlayer().getScene(), session.getPlayer(), notify);
                };

        session.getPlayer().getScene().onPlayerCreateGadget(gadget);
        IneffaRelayHelper.onClientRelayCreated(session.getPlayer(), gadget);
        VentiSkillObjHelper.onClientSkillObjCreated(session.getPlayer(), gadget);
    }
}
'''

ABILITY_PATCHER = r'''
import javassist.*;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.nio.file.*;

public class VentiAbilityPatch {
    public static void main(String[] args) throws Exception {
        String jarPath = args[0];
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jarPath);

        CtClass ct = pool.get("emu.grasscutter.game.ability.AbilityManager");
        CtClass entityCl = pool.get("emu.grasscutter.game.entity.GameEntity");
        CtClass dataCl = pool.get("emu.grasscutter.data.binout.AbilityData");
        CtMethod m = ct.getDeclaredMethod("addAbilityToEntity", new CtClass[]{entityCl, dataCl});
        m.setBody(
            "{"
          + "  emu.grasscutter.game.ability.Ability ability ="
          + "      new emu.grasscutter.game.ability.Ability($2, $1, this.player);"
          + "  $1.getInstancedAbilities().add(ability);"
          + "  if ($1 instanceof emu.grasscutter.game.entity.EntityClientGadget) {"
          + "    emu.grasscutter.game.entity.EntityClientGadget clientGadget ="
          + "        (emu.grasscutter.game.entity.EntityClientGadget) $1;"
          + "    emu.grasscutter.game.entity.GameEntity ownerEntity = null;"
          + "    if (this.player.getScene() != null) {"
          + "      ownerEntity = this.player.getScene().getEntityById(clientGadget.getOriginalOwnerEntityId());"
          + "    }"
          + "    if (ownerEntity instanceof emu.grasscutter.game.entity.EntityAvatar) {"
          + "      applyAvatarTalentSpecials("
          + "          ability,"
          + "          ((emu.grasscutter.game.entity.EntityAvatar) ownerEntity).getAvatar());"
          + "    }"
          + "  }"
          + "  fireAbilityOnAdded(ability, $1);"
          + "}"
        );

        byte[] bytes = ct.toBytecode();
        ct.detach();

        Path tmp = Paths.get(jarPath + ".tmp-venti-ability");
        try (JarFile jin = new JarFile(jarPath);
             JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            Enumeration<JarEntry> en = jin.entries();
            String target = "emu/grasscutter/game/ability/AbilityManager.class";
            Set<String> seen = new HashSet<>();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (n.equals(target) || seen.contains(n)) continue;
                seen.add(n);
                jos.putNextEntry(new JarEntry(n));
                try (InputStream in = jin.getInputStream(e)) {
                    in.transferTo(jos);
                }
                jos.closeEntry();
            }
            jos.putNextEntry(new JarEntry(target));
            jos.write(bytes);
            jos.closeEntry();
        }
        Files.move(tmp, Paths.get(jarPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("AbilityManager patched");
    }
}
'''


def write_build_sources() -> list[Path]:
    if BUILD.exists():
        shutil.rmtree(BUILD)
    paths = []

    def put(rel: str, text: str | None = None, src: Path | None = None) -> Path:
        dest = BUILD / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        if src is not None:
            shutil.copy2(src, dest)
        else:
            dest.write_text(text or "", encoding="utf-8")
        paths.append(dest)
        return dest

    put(
        "emu/grasscutter/game/ability/VentiSkillObjHelper.java",
        src=ROOT / "src/main/java/emu/grasscutter/game/ability/VentiSkillObjHelper.java",
    )
    put("emu/grasscutter/game/ability/actions/ActionCreateGadget.java", ACTION_CREATE_GADGET)
    put(
        "emu/grasscutter/server/packet/recv/HandlerEvtCreateGadgetNotify.java",
        HANDLER_EVT,
    )
    put(
        "emu/grasscutter/server/packet/recv/HandlerCombatInvocationsNotify.java",
        src=ROOT
        / "src/main/java/emu/grasscutter/server/packet/recv/HandlerCombatInvocationsNotify.java",
    )
    return paths


def inject_classes(adds: dict[str, Path], bak_prefix: str) -> Path:
    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    bak = ROOT / f"LunaGC-7.0.0.jar.bak-before-{bak_prefix}-{stamp}"
    shutil.copy2(JAR, bak)
    tmp = ROOT / f"LunaGC-7.0.0.jar.tmp-{bak_prefix}"
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
    return bak


def main() -> int:
    if not JAVAC.exists() or not JAVA.exists():
        ERR_LOG.write_text("jdk tools not found\n", encoding="utf-8")
        return 1
    if not JAR.exists():
        ERR_LOG.write_text(f"jar not found: {JAR}\n", encoding="utf-8")
        return 1

    srcs = write_build_sources()
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    cp_parts = [str(JAR)]
    if LOMBOK.exists():
        cp_parts.insert(0, str(LOMBOK))
    cp = os.pathsep.join(cp_parts)

    cmd = [
        str(JAVAC),
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
    cmd.extend(str(p) for p in srcs)

    r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True)
    err = (r.stderr or b"").decode("utf-8", "replace")
    out = (r.stdout or b"").decode("utf-8", "replace")
    ERR_LOG.write_text(f"return={r.returncode}\n{err}\n{out}", encoding="utf-8")
    if r.returncode != 0:
        sys.stderr.buffer.write(b"javac failed; see _venti_javac_err.txt\n")
        return r.returncode

    adds = {}
    for root, _, fs in os.walk(OUT):
        for f in fs:
            if f.endswith(".class"):
                full = Path(root) / f
                adds[full.relative_to(OUT).as_posix()] = full

    bak = inject_classes(adds, "venti")

    # javassist AbilityManager
    if PATCH_DIR.exists():
        shutil.rmtree(PATCH_DIR)
    PATCH_DIR.mkdir(parents=True)
    patcher = PATCH_DIR / "VentiAbilityPatch.java"
    # Keep single backslashes out of Java string escapes in the patcher source.
    patcher.write_text(ABILITY_PATCHER, encoding="utf-8")
    pr = subprocess.run(
        [str(JAVAC), "-cp", str(JAVASSIST), str(patcher)],
        cwd=str(PATCH_DIR),
        capture_output=True,
    )
    if pr.returncode != 0:
        msg = (pr.stderr or b"").decode("utf-8", "replace")
        ERR_LOG.write_text(ERR_LOG.read_text(encoding="utf-8") + "\njavassist compile:\n" + msg, encoding="utf-8")
        sys.stderr.buffer.write(b"javassist patcher compile failed\n")
        return pr.returncode

    pr2 = subprocess.run(
        [str(JAVA), "-cp", f"{PATCH_DIR};{JAVASSIST}", "VentiAbilityPatch", str(JAR)],
        cwd=str(ROOT),
        capture_output=True,
    )
    if pr2.returncode != 0:
        msg = (pr2.stderr or b"").decode("utf-8", "replace") + (pr2.stdout or b"").decode("utf-8", "replace")
        ERR_LOG.write_text(ERR_LOG.read_text(encoding="utf-8") + "\njavassist run:\n" + msg, encoding="utf-8")
        sys.stderr.buffer.write(b"javassist patch failed\n")
        return pr2.returncode

    summary = [
        f"bak={bak.name}",
        f"classes={len(adds)}",
        "AbilityManager=javassist-patched",
    ]
    summary.extend("  " + rel for rel in sorted(adds))
    OK_LOG.write_text("\n".join(summary), encoding="utf-8")
    sys.stderr.buffer.write(f"inject ok classes={len(adds)} bak={bak.name}\n".encode())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
