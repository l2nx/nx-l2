/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.MacroType;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SendMacroList;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.CharacterMacro;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
@SingleWriter
public class MacroList {

    private final Player owner;

    private int revision;

    private int macroId;

    // Registration order is the order the client lists the macros in.
    private final Int2ObjectLinkedOpenHashMap<Macro> macros = new Int2ObjectLinkedOpenHashMap<>();

    public MacroList(Player owner) {
        this.owner = owner;
        revision = 1;
        macroId = 1000;
    }

    public int getRevision() {
        WorldPartition.checkOwner();
        return revision;
    }

    public int getMacroCount() {
        WorldPartition.checkOwner();
        return macros.size();
    }

    /**
     * @return whether a macro other than {@code exceptId} already has this name, ignoring case
     */
    public boolean isNameTaken(String name, int exceptId) {
        WorldPartition.checkOwner();
        for (Macro macro : macros.values()) {
            if ((macro.getId() != exceptId) && macro.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    public void registerMacro(Macro macro) {
        WorldPartition.checkOwner();
        Macro registered = macro;
        if (registered.getId() == 0) {
            while (macros.containsKey(macroId)) {
                macroId++;
            }

            registered = macro.withId(macroId++);
        }

        macros.put(registered.getId(), registered);
        registerMacroInDb(registered);
        sendUpdate();
    }

    public void deleteMacro(int id) {
        WorldPartition.checkOwner();
        final Macro removed = macros.remove(id);
        if (removed != null) {
            deleteMacroFromDb(removed);
        }

        for (Shortcut sc : owner.getAllShortcuts()) {
            if ((sc.getId() == id) && (sc.getType() == ShortcutType.MACRO)) {
                owner.deleteShortcut(sc.getSlot(), sc.getPage());
            }
        }

        sendUpdate();
    }

    public void sendUpdate() {
        WorldPartition.checkOwner();
        revision++;
        if (macros.isEmpty()) {
            owner.sendPacket(new SendMacroList(revision, 0, null));
            return;
        }

        try (ScratchBuffers.Snapshot<Macro> sent = ScratchBuffers.snapshot(macros.values())) {
            for (Macro m : sent) {
                owner.sendPacket(new SendMacroList(revision, sent.size(), m));
            }
        }
    }

    private void registerMacroInDb(Macro macro) {
        final StringBuilder sb = new StringBuilder(300);
        for (MacroCmd cmd : macro.getCommands()) {
            sb.append(cmd.getType().ordinal() + "," + cmd.getD1() + "," + cmd.getD2());
            if ((cmd.getCmd() != null) && !cmd.getCmd().isEmpty()) {
                sb.append("," + cmd.getCmd());
            }
            sb.append(';');
        }

        if (sb.length() > 255) {
            sb.setLength(255);
        }

        try {
            PlayerPersistenceManager.getInstance()
                    .saveMacro(
                            owner.getObjectId(),
                            CharacterMacro.builder()
                                    .macroId(macro.getId())
                                    .icon(macro.getIcon())
                                    .name(macro.getName())
                                    .description(macro.getDescr())
                                    .acronym(macro.getAcronym())
                                    .commands(sb.toString())
                                    .build());
        } catch (DataAccessException e) {
            log.warn("could not store macro:", e);
        }
    }

    private void deleteMacroFromDb(Macro macro) {
        try {
            PlayerPersistenceManager.getInstance().deleteMacro(owner.getObjectId(), macro.getId());
        } catch (DataAccessException e) {
            log.warn("could not delete macro:", e);
        }
    }

    public boolean restoreMe() {
        WorldPartition.checkOwner();
        macros.clear();
        try {
            for (CharacterMacro stored : PlayerPersistenceManager.getInstance().findMacros(owner.getObjectId())) {
                final List<MacroCmd> commands = new ArrayList<>();
                if (stored.commands() != null) {
                    final StringTokenizer st1 = new StringTokenizer(stored.commands(), ";");
                    while (st1.hasMoreTokens()) {
                        final StringTokenizer st = new StringTokenizer(st1.nextToken(), ",");
                        if (st.countTokens() < 3) {
                            continue;
                        }

                        final MacroType type = MacroType.values()[Integer.parseInt(st.nextToken())];
                        final int d1 = Integer.parseInt(st.nextToken());
                        final int d2 = Integer.parseInt(st.nextToken());
                        final String cmd = st.hasMoreTokens() ? st.nextToken() : null;
                        commands.add(new MacroCmd(commands.size(), type, d1, d2, cmd));
                    }
                }

                macros.put(
                        stored.macroId(),
                        new Macro(
                                stored.macroId(),
                                stored.icon(),
                                stored.name(),
                                stored.description(),
                                stored.acronym(),
                                commands));
            }
        } catch (DataAccessException e) {
            log.warn("could not restore macros:", e);
            return false;
        }

        return true;
    }
}
