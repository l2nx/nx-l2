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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.custom.AllowedPlayerRacesConfig;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.config.custom.StartingLocationConfig;
import app.l2nx.highfive.config.custom.StartingTitleConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.xml.FakePlayerData;
import app.l2nx.highfive.data.xml.InitialEquipmentData;
import app.l2nx.highfive.data.xml.InitialShortcutData;
import app.l2nx.highfive.data.xml.PlayerTemplateData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.appearance.PlayerAppearance;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.stat.PlayerStat;
import app.l2nx.highfive.entity.actor.templates.PlayerTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.InitialEquipment;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerCreate;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CharCreateFail;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CharCreateOk;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CharSelectionInfo;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mobius
 */
@Slf4j
public class CharacterCreate extends ClientPacket {

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    // cSdddddddddddd
    private String name;
    private boolean female;
    private int classId;
    private byte hairStyle;
    private byte hairColor;
    private byte face;

    @Override
    protected void readImpl() {
        name = readString();
        readInt(); // race
        female = readInt() != 0;
        classId = readInt();
        readInt(); // _int
        readInt(); // _str
        readInt(); // _con
        readInt(); // _men
        readInt(); // _dex
        readInt(); // _wit
        hairStyle = (byte) readInt();
        hairColor = (byte) readInt();
        face = (byte) readInt();
    }

    @Override
    protected void runImpl() {
        final GameClient client = getClient();

        // Last Verified: May 30, 2009 - Gracia Final - Players are able to create characters with names consisting of
        // as little as 1,2,3 letter/number combinations.
        if (name.isEmpty() || (name.length() > 16)) {
            client.sendPacket(new CharCreateFail(CharCreateFail.REASON_16_ENG_CHARS));
            return;
        }

        if (PlayerConfig.FORBIDDEN_NAMES.length > 0) {
            for (String st : PlayerConfig.FORBIDDEN_NAMES) {
                if (name.toLowerCase().contains(st.toLowerCase())) {
                    client.sendPacket(new CharCreateFail(CharCreateFail.REASON_INCORRECT_NAME));
                    return;
                }
            }
        }

        if (FakePlayerData.getInstance().getProperName(name) != null) {
            client.sendPacket(new CharCreateFail(CharCreateFail.REASON_INCORRECT_NAME));
            return;
        }

        // Last Verified: May 30, 2009 - Gracia Final.
        if (!StringUtils.isAlphanumeric(name) || !isValidName(name)) {
            client.sendPacket(new CharCreateFail(CharCreateFail.REASON_INCORRECT_NAME));
            return;
        }

        if ((face > 2) || (face < 0)) {
            log.warn(
                    "Character Creation Failure: Character face {} is invalid. Possible client hack. {}", face, client);
            client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
            return;
        }

        if ((hairStyle < 0) || (!female && (hairStyle > 4)) || (female && (hairStyle > 6))) {
            log.warn(
                    "Character Creation Failure: Character hair style {} is invalid. Possible client hack. {}",
                    hairStyle,
                    client);
            client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
            return;
        }

        if ((hairColor > 3) || (hairColor < 0)) {
            log.warn(
                    "Character Creation Failure: Character hair color {} is invalid. Possible client hack. {}",
                    hairColor,
                    client);
            client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
            return;
        }

        Player newChar = null;
        PlayerTemplate template = null;

        /*
         * DrHouse: Since checks for duplicate names are done using SQL, lock must be held until data is written to DB as well.
         */
        synchronized (CharInfoTable.getInstance()) {
            if ((CharInfoTable.getInstance().getAccountCharacterCount(client.getAccountId())
                            >= ServerConfig.MAX_CHARACTERS_NUMBER_PER_ACCOUNT)
                    && (ServerConfig.MAX_CHARACTERS_NUMBER_PER_ACCOUNT != 0)) {
                client.sendPacket(new CharCreateFail(CharCreateFail.REASON_TOO_MANY_CHARACTERS));
                return;
            } else if (CharInfoTable.getInstance().doesCharNameExist(name)) {
                client.sendPacket(new CharCreateFail(CharCreateFail.REASON_NAME_ALREADY_EXISTS));
                return;
            }

            template = PlayerTemplateData.getInstance().getTemplate(classId);
            if ((template == null) || (PlayerClass.getPlayerClass(classId).level() > 0)) {
                client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
                return;
            }

            // Custom Feature: Disallow a race to be created.
            // Example: Humans can not be created if AllowHuman = False in Custom.properties.
            switch (template.getRace()) {
                case HUMAN: {
                    if (!AllowedPlayerRacesConfig.ALLOW_HUMAN) {
                        client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
                        return;
                    }
                    break;
                }
                case ELF: {
                    if (!AllowedPlayerRacesConfig.ALLOW_ELF) {
                        client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
                        return;
                    }
                    break;
                }
                case DARK_ELF: {
                    if (!AllowedPlayerRacesConfig.ALLOW_DARKELF) {
                        client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
                        return;
                    }
                    break;
                }
                case ORC: {
                    if (!AllowedPlayerRacesConfig.ALLOW_ORC) {
                        client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
                        return;
                    }
                    break;
                }
                case DWARF: {
                    if (!AllowedPlayerRacesConfig.ALLOW_DWARF) {
                        client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
                        return;
                    }
                    break;
                }
                case KAMAEL: {
                    if (!AllowedPlayerRacesConfig.ALLOW_KAMAEL) {
                        client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
                        return;
                    }
                    break;
                }
            }

            newChar = Player.create(
                    template, client.getAccountId(), name, new PlayerAppearance(face, hairColor, hairStyle, female));
        }

        // HP and MP are at maximum and CP is zero by default.
        newChar.setCurrentHp(newChar.getMaxHp());
        newChar.setCurrentMp(newChar.getMaxMp());
        // newChar.setMaxLoad(template.getBaseLoad());

        initNewChar(client, newChar);
        client.sendPacket(CharCreateOk.STATIC_PACKET);

        ACCOUNTING_LOG.info("Created new character, {}, {}", newChar, client);
    }

    private boolean isValidName(String text) {
        return ServerConfig.CHARNAME_TEMPLATE_PATTERN.matcher(text).matches();
    }

    private void initNewChar(GameClient client, Player newChar) {
        World.addObject(newChar);

        if (PlayerConfig.STARTING_ADENA > 0) {
            newChar.addAdena(ItemProcessType.REWARD, PlayerConfig.STARTING_ADENA, null, false);
        }

        final PlayerTemplate template = newChar.getTemplate();
        if (StartingLocationConfig.CUSTOM_STARTING_LOC) {
            final Location createLoc = new Location(
                    StartingLocationConfig.CUSTOM_STARTING_LOC_X,
                    StartingLocationConfig.CUSTOM_STARTING_LOC_Y,
                    StartingLocationConfig.CUSTOM_STARTING_LOC_Z);
            newChar.setXYZInvisible(createLoc.getX(), createLoc.getY(), createLoc.getZ());
        } else if (FactionSystemConfig.FACTION_SYSTEM_ENABLED) {
            newChar.setXYZInvisible(
                    FactionSystemConfig.FACTION_STARTING_LOCATION.getX(),
                    FactionSystemConfig.FACTION_STARTING_LOCATION.getY(),
                    FactionSystemConfig.FACTION_STARTING_LOCATION.getZ());
        } else {
            final Location createLoc = template.getCreationPoint();
            newChar.setXYZInvisible(createLoc.getX(), createLoc.getY(), createLoc.getZ());
        }

        newChar.setTitle(
                StartingTitleConfig.ENABLE_CUSTOM_STARTING_TITLE ? StartingTitleConfig.CUSTOM_STARTING_TITLE : null);

        if (PlayerConfig.ENABLE_VITALITY) {
            newChar.setVitalityPoints(
                    Math.min(PlayerConfig.STARTING_VITALITY_POINTS, PlayerStat.MAX_VITALITY_POINTS), true);
        }

        if (PlayerConfig.STARTING_LEVEL > 1) {
            newChar.getStat().addLevel((byte) (PlayerConfig.STARTING_LEVEL - 1));
        }

        if (PlayerConfig.STARTING_SP > 0) {
            newChar.getStat().addSp(PlayerConfig.STARTING_SP);
        }

        final Collection<InitialEquipment> classEquipment =
                InitialEquipmentData.getInstance().getClassEquipment(newChar.getPlayerClass());
        if (classEquipment != null) {
            for (InitialEquipment equipment : classEquipment) {
                final Item item = newChar.getInventory()
                        .addItem(ItemProcessType.REWARD, equipment.getId(), equipment.getCount(), newChar, null);
                if (item == null) {
                    log.warn(
                            "Could not create item during player creation: itemId {}, amount {}.",
                            equipment.getId(),
                            equipment.getCount());
                    continue;
                }

                if (item.isEquipable() && equipment.isEquipped()) {
                    newChar.getInventory().equipItem(item);
                }
            }
        }

        for (SkillLearn skill :
                SkillTreeData.getInstance().getAvailableSkills(newChar, newChar.getPlayerClass(), false, true)) {
            newChar.addSkill(SkillData.getInstance().getSkill(skill.getSkillId(), skill.getSkillLevel()), true);
        }

        // Register all shortcuts for actions, skills and items for this new character.
        InitialShortcutData.getInstance().registerAllShortcuts(newChar);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CREATE, Containers.Players())) {
            EventDispatcher.getInstance()
                    .notifyEvent(
                            OnPlayerCreate.builder()
                                    .player(newChar)
                                    .objectId(newChar.getObjectId())
                                    .name(newChar.getName())
                                    .client(client)
                                    .build(),
                            Containers.Players());
        }

        newChar.setOnlineStatus(true, false);
        Disconnection.of(client, newChar).storeAndDelete();

        final CharSelectionInfo cl = new CharSelectionInfo(client);
        client.setCharSelection(cl.getCharInfo());
    }
}
