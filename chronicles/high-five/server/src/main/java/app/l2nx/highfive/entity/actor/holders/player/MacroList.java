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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.MacroType;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.network.serverpackets.SendMacroList;
import app.l2nx.highfive.repository.character.model.CharacterMacro;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
public class MacroList {

    private final Player owner;

    @Getter
    private int revision;

    private int macroId;

    @Getter
    private final Map<Integer, Macro> allMacroses = Collections.synchronizedMap(new LinkedHashMap<>());

    public MacroList(Player owner) {
        this.owner = owner;
        revision = 1;
        macroId = 1000;
    }

    public void registerMacro(Macro macro) {
        if (macro.getId() == 0) {
            macro.setId(macroId++);
            while (allMacroses.containsKey(macro.getId())) {
                macro.setId(macroId++);
            }

            allMacroses.put(macro.getId(), macro);
            registerMacroInDb(macro);
        } else {
            allMacroses.put(macro.getId(), macro);
            registerMacroInDb(macro);
        }

        sendUpdate();
    }

    public void deleteMacro(int id) {
        final Macro removed = allMacroses.remove(id);
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
        revision++;
        final Collection<Macro> allMacros = allMacroses.values();
        synchronized (allMacroses) {
            if (allMacros.isEmpty()) {
                owner.sendPacket(new SendMacroList(revision, 0, null));
            } else {
                for (Macro m : allMacros) {
                    owner.sendPacket(new SendMacroList(revision, allMacros.size(), m));
                }
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
        allMacroses.clear();
        try {
            for (CharacterMacro stored : PlayerPersistenceManager.getInstance().findMacros(owner.getObjectId())) {
                final List<MacroCmd> commands = new ArrayList<>();
                final StringTokenizer st1 =
                        new StringTokenizer(stored.commands() == null ? "" : stored.commands(), ";");
                while (st1.hasMoreTokens()) {
                    final StringTokenizer st = new StringTokenizer(st1.nextToken(), ",");
                    if (st.countTokens() < 3) {
                        continue;
                    }

                    final MacroType type = MacroType.values()[Integer.parseInt(st.nextToken())];
                    final int d1 = Integer.parseInt(st.nextToken());
                    final int d2 = Integer.parseInt(st.nextToken());
                    String cmd = "";
                    if (st.hasMoreTokens()) {
                        cmd = st.nextToken();
                    }

                    commands.add(new MacroCmd(commands.size(), type, d1, d2, cmd));
                }

                allMacroses.put(
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
