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
package app.l2nx.highfive.data.xml;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Secondary Auth data.
 * @author NosBit
 */
@Slf4j
public class SecondaryAuthData implements IXmlReader {
    @Getter
    private final Set<String> forbiddenPasswords = new HashSet<>();

    @Getter
    private boolean enabled = false;

    @Getter
    private int maxAttempts = 5;

    @Getter
    private int banTime = 480;

    @Getter
    private String recoveryLink = "";

    protected SecondaryAuthData() {
        load();
    }

    @Override
    public synchronized void load() {
        forbiddenPasswords.clear();
        parseFile(new File("config/SecondaryAuth.xml"));
        log.info("Loaded {} forbidden passwords.", forbiddenPasswords.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        try {
            for (Node node = document.getFirstChild(); node != null; node = node.getNextSibling()) {
                if ("list".equalsIgnoreCase(node.getNodeName())) {
                    for (Node list_node = node.getFirstChild();
                            list_node != null;
                            list_node = list_node.getNextSibling()) {
                        if ("enabled".equalsIgnoreCase(list_node.getNodeName())) {
                            enabled = Boolean.parseBoolean(list_node.getTextContent());
                        } else if ("maxAttempts".equalsIgnoreCase(list_node.getNodeName())) {
                            maxAttempts = Integer.parseInt(list_node.getTextContent());
                        } else if ("banTime".equalsIgnoreCase(list_node.getNodeName())) {
                            banTime = Integer.parseInt(list_node.getTextContent());
                        } else if ("recoveryLink".equalsIgnoreCase(list_node.getNodeName())) {
                            recoveryLink = list_node.getTextContent();
                        } else if ("forbiddenPasswords".equalsIgnoreCase(list_node.getNodeName())) {
                            for (Node forbiddenPasswords_node = list_node.getFirstChild();
                                    forbiddenPasswords_node != null;
                                    forbiddenPasswords_node = forbiddenPasswords_node.getNextSibling()) {
                                if ("password".equalsIgnoreCase(forbiddenPasswords_node.getNodeName())) {
                                    forbiddenPasswords.add(forbiddenPasswords_node.getTextContent());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load secondary auth data from xml.", e);
        }
    }

    /**
     * Checks if a given password is in the list of forbidden passwords.
     * @param password the password to check
     * @return {@code true} if the password is forbidden; {@code false} otherwise
     */
    public boolean isForbiddenPassword(String password) {
        return forbiddenPasswords.contains(password);
    }

    public static SecondaryAuthData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final SecondaryAuthData INSTANCE = new SecondaryAuthData();
    }
}
