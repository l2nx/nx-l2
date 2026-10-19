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

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.MacroType;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.network.serverpackets.SendMacroList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
            final Macro old = allMacroses.put(macro.getId(), macro);
            if (old != null) {
                deleteMacroFromDb(old);
            }

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
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO character_macroses (charId,id,icon,name,descr,acronym,commands) values(?,?,?,?,?,?,?)")) {
            ps.setInt(1, owner.getObjectId());
            ps.setInt(2, macro.getId());
            ps.setInt(3, macro.getIcon());
            ps.setString(4, macro.getName());
            ps.setString(5, macro.getDescr());
            ps.setString(6, macro.getAcronym());
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

            ps.setString(7, sb.toString());
            ps.execute();
        } catch (Exception e) {
            log.warn("could not store macro:", e);
        }
    }

    private void deleteMacroFromDb(Macro macro) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM character_macroses WHERE charId=? AND id=?")) {
            ps.setInt(1, owner.getObjectId());
            ps.setInt(2, macro.getId());
            ps.execute();
        } catch (Exception e) {
            log.warn("could not delete macro:", e);
        }
    }

    public boolean restoreMe() {
        allMacroses.clear();
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT charId, id, icon, name, descr, acronym, commands FROM character_macroses WHERE charId=?")) {
            ps.setInt(1, owner.getObjectId());
            try (ResultSet rset = ps.executeQuery()) {
                while (rset.next()) {
                    final int id = rset.getInt("id");
                    final int icon = rset.getInt("icon");
                    final String name = rset.getString("name");
                    final String descr = rset.getString("descr");
                    final String acronym = rset.getString("acronym");
                    final List<MacroCmd> commands = new ArrayList<>();
                    final StringTokenizer st1 = new StringTokenizer(rset.getString("commands"), ";");
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

                    allMacroses.put(id, new Macro(id, icon, name, descr, acronym, commands));
                }
            }
        } catch (Exception e) {
            log.warn("could not store shortcuts:", e);
            return false;
        }

        return true;
    }
}
