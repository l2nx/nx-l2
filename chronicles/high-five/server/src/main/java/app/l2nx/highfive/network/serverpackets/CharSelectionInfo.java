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
package app.l2nx.highfive.network.serverpackets;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.highfive.network.holders.CharacterInfoHolder;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.Faction;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
public class CharSelectionInfo extends ServerPacket {
    private final String loginName;
    private final int sessionId;
    private int activeId;

    @Getter
    private final List<CharacterInfoHolder> charInfo;

    public CharSelectionInfo(GameClient client) {
        this(client, -1);
    }

    /**
     * @param activeId the slot to mark as selected, {@code -1} for the character accessed last
     */
    public CharSelectionInfo(GameClient client, int activeId) {
        sessionId = client.getSessionId().playOk1();
        loginName = client.getAccountName();
        charInfo = loadCharacterSelectInfo(client.getAccountId());
        this.activeId = activeId;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.CHARACTER_SELECTION_INFO.writeId(this, buffer);
        final int size = charInfo.size();
        buffer.writeInt(size); // Created character count
        buffer.writeInt(
                ServerConfig
                        .MAX_CHARACTERS_NUMBER_PER_ACCOUNT); // Can prevent players from creating new characters (if 0);
        // (if 1, the client will ask if chars may be created
        // (0x13) Response: (0x0D) ).
        buffer.writeByte(0);
        long lastAccess = 0;
        if (activeId == -1) {
            for (int i = 0; i < size; i++) {
                if (lastAccess < charInfo.get(i).getLastAccess()) {
                    lastAccess = charInfo.get(i).getLastAccess();
                    activeId = i;
                }
            }
        }

        for (int i = 0; i < size; i++) {
            final CharacterInfoHolder charInfoPackage = charInfo.get(i);
            buffer.writeString(charInfoPackage.getName()); // Character name
            buffer.writeInt(charInfoPackage.getObjectId()); // Character ID
            buffer.writeString(loginName); // Account name
            buffer.writeInt(sessionId); // Account ID
            buffer.writeInt(charInfoPackage.getClanId()); // Clan ID
            buffer.writeInt(0); // Builder level
            buffer.writeInt(charInfoPackage.getSex()); // Sex
            buffer.writeInt(charInfoPackage.getRace()); // Race
            buffer.writeInt(charInfoPackage.getBaseClassId());
            buffer.writeInt(1); // GameServerName
            buffer.writeInt(charInfoPackage.getX());
            buffer.writeInt(charInfoPackage.getY());
            buffer.writeInt(charInfoPackage.getZ());
            buffer.writeDouble(charInfoPackage.getCurrentHp());
            buffer.writeDouble(charInfoPackage.getCurrentMp());
            buffer.writeInt((int) charInfoPackage.getSp());
            buffer.writeLong(charInfoPackage.getExp());
            buffer.writeDouble((float) (charInfoPackage.getExp()
                            - ExperienceData.getInstance().getExpForLevel(charInfoPackage.getLevel()))
                    / (ExperienceData.getInstance().getExpForLevel(charInfoPackage.getLevel() + 1)
                            - ExperienceData.getInstance().getExpForLevel(charInfoPackage.getLevel()))); // High

            // Five
            buffer.writeInt(charInfoPackage.getLevel());
            buffer.writeInt(charInfoPackage.getKarma());
            buffer.writeInt(charInfoPackage.getPkKills());
            buffer.writeInt(charInfoPackage.getPvPKills());
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            for (int slot : getPaperdollOrder()) {
                buffer.writeInt(charInfoPackage.getPaperdollItemId(slot));
            }

            buffer.writeInt(charInfoPackage.getHairStyle());
            buffer.writeInt(charInfoPackage.getHairColor());
            buffer.writeInt(charInfoPackage.getFace());
            buffer.writeDouble(charInfoPackage.getMaxHp()); // Maximum HP
            buffer.writeDouble(charInfoPackage.getMaxMp()); // Maximum MP

            if (charInfoPackage.getAccessLevel() > -1) {
                // Deleted.
                if (charInfoPackage.getDeleteTimer() > 0) {
                    buffer.writeInt((int) ((charInfoPackage.getDeleteTimer() - ClockBridge.wallMillis()) / 1000));
                } else // Normal.
                {
                    buffer.writeInt(0);
                }
            } else // Banned.
            {
                buffer.writeInt(-1);
            }

            buffer.writeInt(charInfoPackage.getClassId());
            buffer.writeInt(i == activeId);
            buffer.writeByte(Math.min(charInfoPackage.getEnchantEffect(), 127));
            buffer.writeInt(charInfoPackage.getAugmentationId());
            // buffer.writeInt(charInfoPackage.getTransformId()); // Used to display Transformations
            buffer.writeInt(
                    0); // Currently on retail when you are on character select you don't see your transformation.
            buffer.writeInt(0); // Pet NpcId
            buffer.writeInt(0); // Pet level
            buffer.writeInt(0); // Pet Food
            buffer.writeInt(0); // Pet Food Level
            buffer.writeDouble(0); // Current pet HP
            buffer.writeDouble(0); // Current pet MP
            buffer.writeInt(charInfoPackage.getVitalityPoints()); // Vitality
        }
    }

    private static List<CharacterInfoHolder> loadCharacterSelectInfo(UUID accountId) {
        final List<CharacterInfoHolder> characterList = new ArrayList<>();
        try {
            for (CharacterRow character : PlayerPersistenceManager.getInstance().findCharacters(accountId)) {
                final CharacterInfoHolder charInfopackage = restoreChar(character);
                if (charInfopackage == null) {
                    continue;
                }

                characterList.add(charInfopackage);

                // Disconnect offline trader.
                if (OfflineTradeConfig.OFFLINE_DISCONNECT_SAME_ACCOUNT) {
                    final Player player = World.getPlayer(charInfopackage.getObjectId());
                    if ((player != null) && player.isInStoreMode()) {
                        Disconnection.of(player).storeAndDelete();
                        continue;
                    }
                }

                // Disconnect offline play.
                if (OfflinePlayConfig.OFFLINE_PLAY_DISCONNECT_SAME_ACCOUNT) {
                    final Player player = World.getPlayer(charInfopackage.getObjectId());
                    if ((player != null) && player.isOfflinePlay()) {
                        Disconnection.of(player).storeAndDelete();
                    }
                }
            }
        } catch (DataAccessException e) {
            log.warn("Could not restore char info: {}", e.getMessage(), e);
        }

        return characterList;
    }

    private static void loadCharacterSubclassInfo(
            CharacterInfoHolder charInfopackage, int objectId, int activeClassId) {
        PlayerPersistenceManager.getInstance()
                .findSubclass(objectId, activeClassId)
                .ifPresent(subclass -> {
                    charInfopackage.setExp(subclass.exp());
                    charInfopackage.setSp(subclass.sp());
                    charInfopackage.setLevel(subclass.level());
                });
    }

    private static CharacterInfoHolder restoreChar(CharacterRow character) {
        final int objectId = character.objectId();
        final String name = character.name();

        // See if the char must be deleted.
        final long deletetime = epochMillisOrZero(character.deleteAt());
        if ((deletetime > 0) && (ClockBridge.wallMillis() > deletetime)) {
            final Clan clan = ClanTable.getInstance().getClan(character.clanId());
            if (clan != null) {
                clan.removeClanMember(objectId, 0);
            }

            GameClient.deleteCharByObjId(objectId);
            return null;
        }

        final CharacterInfoHolder charInfopackage = new CharacterInfoHolder(objectId, name);
        charInfopackage.setAccessLevel(character.accessLevel());
        charInfopackage.setLevel(character.level());
        charInfopackage.setMaxHp(character.maxHp());
        charInfopackage.setCurrentHp(character.currentHp());
        charInfopackage.setMaxMp(character.maxMp());
        charInfopackage.setCurrentMp(character.currentMp());
        charInfopackage.setKarma(character.karma());
        charInfopackage.setPkKills(character.pkKills());
        charInfopackage.setPvPKills(character.pvpKills());
        charInfopackage.setFace(character.face());
        charInfopackage.setHairStyle(character.hairStyle());
        charInfopackage.setHairColor(character.hairColor());
        charInfopackage.setSex(character.sex().ordinal());
        charInfopackage.setExp(character.exp());
        charInfopackage.setSp(character.sp());
        charInfopackage.setVitalityPoints(character.vitalityPoints());
        charInfopackage.setClanId(character.clanId());
        charInfopackage.setRace(character.race().ordinal());
        final int baseClassId = character.baseClassId();
        final int activeClassId = character.classId();
        charInfopackage.setX(character.x());
        charInfopackage.setY(character.y());
        charInfopackage.setZ(character.z());
        if (character.faction() == Faction.GOOD) {
            charInfopackage.setGood();
        } else if (character.faction() == Faction.EVIL) {
            charInfopackage.setEvil();
        }

        if (MultilingualSupportConfig.MULTILANG_ENABLE) {
            String lang = character.language();
            if (!MultilingualSupportConfig.MULTILANG_ALLOWED.contains(lang)) {
                lang = MultilingualSupportConfig.MULTILANG_DEFAULT;
            }

            charInfopackage.setHtmlPrefix("data/lang/" + lang + "/");
        }

        // If is in subclass, load subclass exp, sp, level info.
        if (baseClassId != activeClassId) {
            loadCharacterSubclassInfo(charInfopackage, objectId, activeClassId);
        }

        charInfopackage.setClassId(activeClassId);

        // Get the augmentation id for equipped weapon.
        final int weaponObjId = charInfopackage.getPaperdollObjectId(Inventory.PAPERDOLL_RHAND);
        if (weaponObjId > 0) {
            ItemManager.getInstance().findAugmentation(weaponObjId).ifPresent(charInfopackage::setAugmentationId);
        }

        // Check if the base class is set to zero and also doesn't match with the current active class, otherwise send
        // the base class ID. This prevents chars created before base class was introduced from being displayed
        // incorrectly.
        if ((baseClassId == 0) && (activeClassId > 0)) {
            charInfopackage.setBaseClassId(activeClassId);
        } else {
            charInfopackage.setBaseClassId(baseClassId);
        }

        charInfopackage.setDeleteTimer(deletetime);
        charInfopackage.setLastAccess(epochMillisOrZero(character.lastAccessAt()));
        return charInfopackage;
    }
}
