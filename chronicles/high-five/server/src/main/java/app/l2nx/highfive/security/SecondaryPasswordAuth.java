/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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

import app.l2nx.highfive.data.xml.SecondaryAuthData;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.managers.AccountVariablesManager;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Ex2ndPasswordAck;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Ex2ndPasswordCheck;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Ex2ndPasswordVerify;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LeaveWorld;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;

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

    private static final String VAR_PREFIX = "secauth_";
    private static final String VAR_PWD = VAR_PREFIX + "pwd";
    private static final String VAR_WTE = VAR_PREFIX + "wte";

    public SecondaryPasswordAuth(GameClient activeClient) {
        this.activeClient = activeClient;
        password = null;
        wrongAttempts = 0;
        authed = false;
        loadPassword();
    }

    private void loadPassword() {
        try {
            final Map<String, String> variables =
                    AccountVariablesManager.getInstance().findVariables(activeClient.getAccountId(), VAR_PREFIX);
            password = variables.get(VAR_PWD);
            final String attempts = variables.get(VAR_WTE);
            if (attempts != null) {
                wrongAttempts = Integer.parseInt(attempts);
            }
        } catch (DataAccessException | NumberFormatException e) {
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
        try {
            AccountVariablesManager.getInstance().insertVariable(activeClient.getAccountId(), VAR_PWD, password);
        } catch (DataAccessException e) {
            log.error("Error while writing password.", e);
            return false;
        }

        this.password = password;
        return true;
    }

    public boolean insertWrongAttempt(int attempts) {
        try {
            AccountVariablesManager.getInstance()
                    .saveVariable(activeClient.getAccountId(), VAR_WTE, Integer.toString(attempts));
        } catch (DataAccessException e) {
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
        try {
            AccountVariablesManager.getInstance().updateVariable(activeClient.getAccountId(), VAR_PWD, password);
        } catch (DataAccessException e) {
            log.error("Error while writing password.", e);
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
                LoginService.getInstance()
                        .requestTempBan(
                                activeClient.getAccountId(),
                                activeClient.getIp(),
                                Duration.ofMinutes(
                                        SecondaryAuthData.getInstance().getBanTime()));
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
