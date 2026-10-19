/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.security;

import app.l2nx.highfive.LoginServerThread;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.xml.SecondaryAuthData;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.Ex2ndPasswordAck;
import app.l2nx.highfive.network.serverpackets.Ex2ndPasswordCheck;
import app.l2nx.highfive.network.serverpackets.Ex2ndPasswordVerify;
import app.l2nx.highfive.network.serverpackets.LeaveWorld;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author mrTJO
 */
@Slf4j
public class SecondaryPasswordAuth {
    private final GameClient activeClient;

    private String password;
    private int wrongAttempts;

    @Getter
    private boolean authed;

    private static final String VAR_PWD = "secauth_pwd";
    private static final String VAR_WTE = "secauth_wte";

    private static final String SELECT_PASSWORD =
            "SELECT var, value FROM account_gsdata WHERE account_name=? AND var LIKE 'secauth_%'";
    private static final String INSERT_PASSWORD = "INSERT INTO account_gsdata VALUES (?, ?, ?)";
    private static final String UPDATE_PASSWORD = "UPDATE account_gsdata SET value=? WHERE account_name=? AND var=?";

    private static final String INSERT_ATTEMPT =
            "INSERT INTO account_gsdata VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value=?";

    /**
     * @param activeClient
     */
    public SecondaryPasswordAuth(GameClient activeClient) {
        this.activeClient = activeClient;
        password = null;
        wrongAttempts = 0;
        authed = false;
        loadPassword();
    }

    private void loadPassword() {
        String var = null;
        String value = null;
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(SELECT_PASSWORD)) {
            statement.setString(1, activeClient.getAccountName());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    var = rs.getString("var");
                    value = rs.getString("value");
                    if (var.equals(VAR_PWD)) {
                        password = value;
                    } else if (var.equals(VAR_WTE)) {
                        wrongAttempts = Integer.parseInt(value);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error while reading password.", e);
        }
    }

    public boolean savePassword(String value) {
        if (passwordExist()) {
            log.warn("[SecondaryPasswordAuth]{} forced savePassword", activeClient.getAccountName());
            Disconnection.of(activeClient).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
            return false;
        }

        if (!validatePassword(value)) {
            activeClient.sendPacket(new Ex2ndPasswordAck(Ex2ndPasswordAck.WRONG_PATTERN));
            return false;
        }

        final String password = cryptPassword(value);
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(INSERT_PASSWORD)) {
            statement.setString(1, activeClient.getAccountName());
            statement.setString(2, VAR_PWD);
            statement.setString(3, password);
            statement.execute();
        } catch (Exception e) {
            log.error("Error while writing password.", e);
            return false;
        }

        this.password = password;
        return true;
    }

    public boolean insertWrongAttempt(int attempts) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(INSERT_ATTEMPT)) {
            statement.setString(1, activeClient.getAccountName());
            statement.setString(2, VAR_WTE);
            statement.setString(3, Integer.toString(attempts));
            statement.setString(4, Integer.toString(attempts));
            statement.execute();
        } catch (Exception e) {
            log.error("Error while writing wrong attempts.", e);
            return false;
        }

        return true;
    }

    public boolean changePassword(String oldPassword, String newPassword) {
        if (!passwordExist()) {
            log.warn("[SecondaryPasswordAuth]{} forced changePassword", activeClient.getAccountName());
            Disconnection.of(activeClient).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
            return false;
        }

        if (!checkPassword(oldPassword, true)) {
            return false;
        }

        if (!validatePassword(newPassword)) {
            activeClient.sendPacket(new Ex2ndPasswordAck(Ex2ndPasswordAck.WRONG_PATTERN));
            return false;
        }

        final String password = cryptPassword(newPassword);
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(UPDATE_PASSWORD)) {
            statement.setString(1, password);
            statement.setString(2, activeClient.getAccountName());
            statement.setString(3, VAR_PWD);
            statement.execute();
        } catch (Exception e) {
            log.error("Error while reading password.", e);
            return false;
        }

        this.password = password;
        authed = false;
        return true;
    }

    public boolean checkPassword(String value, boolean skipAuth) {
        final String password = cryptPassword(value);
        if (!password.equals(this.password)) {
            wrongAttempts++;
            if (wrongAttempts < SecondaryAuthData.getInstance().getMaxAttempts()) {
                activeClient.sendPacket(new Ex2ndPasswordVerify(Ex2ndPasswordVerify.PASSWORD_WRONG, wrongAttempts));
                insertWrongAttempt(wrongAttempts);
            } else {
                LoginServerThread.getInstance()
                        .sendTempBan(
                                activeClient.getAccountName(),
                                activeClient.getIp(),
                                SecondaryAuthData.getInstance().getBanTime());
                LoginServerThread.getInstance()
                        .sendMail(
                                activeClient.getAccountName(),
                                "SATempBan",
                                activeClient.getIp(),
                                Integer.toString(SecondaryAuthData.getInstance().getMaxAttempts()),
                                Long.toString(SecondaryAuthData.getInstance().getBanTime()),
                                SecondaryAuthData.getInstance().getRecoveryLink());
                log.warn(
                        "{} - ({}) has inputted the wrong password {} times in row.",
                        activeClient.getAccountName(),
                        activeClient.getIp(),
                        wrongAttempts);
                insertWrongAttempt(0);
                activeClient.close(new Ex2ndPasswordVerify(
                        Ex2ndPasswordVerify.PASSWORD_BAN,
                        SecondaryAuthData.getInstance().getMaxAttempts()));
            }

            return false;
        }

        if (!skipAuth) {
            authed = true;
            activeClient.sendPacket(new Ex2ndPasswordVerify(Ex2ndPasswordVerify.PASSWORD_OK, wrongAttempts));
        }

        insertWrongAttempt(0);
        return true;
    }

    public boolean passwordExist() {
        return password != null;
    }

    public void openDialog() {
        if (passwordExist()) {
            activeClient.sendPacket(new Ex2ndPasswordCheck(Ex2ndPasswordCheck.PASSWORD_PROMPT));
        } else {
            activeClient.sendPacket(new Ex2ndPasswordCheck(Ex2ndPasswordCheck.PASSWORD_NEW));
        }
    }

    private String cryptPassword(String password) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA");
            final byte[] raw = password.getBytes(StandardCharsets.UTF_8);
            final byte[] hash = md.digest(raw);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("[SecondaryPasswordAuth]Unsupported Algorythm");
        }

        return null;
    }

    private boolean validatePassword(String password) {
        if (!StringUtils.isNumeric(password)) {
            return false;
        }

        if ((password.length() < 6) || (password.length() > 8)) {
            return false;
        }

        return !SecondaryAuthData.getInstance().isForbiddenPassword(password);
    }
}
