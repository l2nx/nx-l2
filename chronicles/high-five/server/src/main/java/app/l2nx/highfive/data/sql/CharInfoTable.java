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
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.entity.actor.Player;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CharInfoTable {

    private final Map<Integer, String> names = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> accessLevels = new ConcurrentHashMap<>();

    protected CharInfoTable() {
        try (Connection con = DatabaseFactory.getConnection();
                Statement s = con.createStatement();
                ResultSet rs = s.executeQuery("SELECT charId, char_name, accesslevel FROM characters")) {
            while (rs.next()) {
                final int id = rs.getInt("charId");
                names.put(id, rs.getString("char_name"));
                accessLevels.put(id, rs.getInt("accesslevel"));
            }
        } catch (SQLException e) {
            log.warn("Couldn't retrieve all char id/name/access: {}", e.getMessage(), e);
        }

        log.info("Loaded {} char names.", names.size());
    }

    public void addName(Player player) {
        if (player != null) {
            addName(player.getObjectId(), player.getName());
            accessLevels.put(player.getObjectId(), player.getAccessLevel().getLevel());
        }
    }

    private void addName(int objectId, String name) {
        if ((name != null) && !name.equals(names.get(objectId))) {
            names.put(objectId, name);
        }
    }

    public void removeName(int objId) {
        names.remove(objId);
        accessLevels.remove(objId);
    }

    public int getIdByName(String name) {
        if ((name == null) || name.isEmpty()) {
            return -1;
        }

        for (Entry<Integer, String> entry : names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }

        // Should not continue after the above?

        int id = -1;
        int accessLevel = 0;

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT charId,accesslevel FROM characters WHERE char_name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    id = rs.getInt("charId");
                    accessLevel = rs.getInt("accesslevel");
                }
            }
        } catch (SQLException e) {
            log.warn("Could not check existing char name: {}", e.getMessage(), e);
        }

        if (id > 0) {
            names.put(id, name);
            accessLevels.put(id, accessLevel);
            return id;
        }

        return -1; // Not found.
    }

    public String getNameById(int id) {
        if (id <= 0) {
            return null;
        }

        String name = names.get(id);
        if (name != null) {
            return name;
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT char_name,accesslevel FROM characters WHERE charId=?")) {
            ps.setInt(1, id);
            try (ResultSet rset = ps.executeQuery()) {
                if (rset.next()) {
                    name = rset.getString("char_name");
                    names.put(id, name);
                    accessLevels.put(id, rset.getInt("accesslevel"));
                    return name;
                }
            }
        } catch (SQLException e) {
            log.warn("Could not check existing char id: {}", e.getMessage(), e);
        }

        return null; // not found
    }

    public int getAccessLevelById(int objectId) {
        return getNameById(objectId) != null ? accessLevels.get(objectId) : 0;
    }

    public synchronized boolean doesCharNameExist(String name) {
        boolean result = false;
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT COUNT(*) as count FROM characters WHERE char_name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result = rs.getInt("count") > 0;
                }
            }
        } catch (SQLException e) {
            log.warn("Could not check existing charname: {}", e.getMessage(), e);
        }

        return result;
    }

    public int getAccountCharacterCount(String account) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT COUNT(char_name) as count FROM characters WHERE account_name=?")) {
            ps.setString(1, account);
            try (ResultSet rset = ps.executeQuery()) {
                if (rset.next()) {
                    return rset.getInt("count");
                }
            }
        } catch (SQLException e) {
            log.warn("Couldn't retrieve account for id: {}", e.getMessage(), e);
        }

        return 0;
    }

    public static CharInfoTable getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final CharInfoTable INSTANCE = new CharInfoTable();
    }
}
