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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.data.holders.PetData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.MagicSkillLaunched;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import lombok.extern.slf4j.Slf4j;

public class PetManager extends Merchant {
    /**
     * Creates a pet manager.
     * @param template the pet manager NPC template.
     */
    public PetManager(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.PetManager);
    }

    @Override
    public String getHtmlPath(int npcId, int value) {
        String pom = "";
        if (value == 0) {
            pom = Integer.toString(npcId);
        } else {
            pom = npcId + "-" + value;
        }

        return "data/html/petmanager/" + pom + ".htm";
    }

    @Override
    public void showChatWindow(Player player) {
        String filename = "data/html/petmanager/" + getId() + ".htm";
        if ((getId() == 36478) && player.hasSummon()) {
            filename = "data/html/petmanager/restore-unsummonpet.htm";
        }

        final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
        html.setFile(player, filename);
        if (GeneralConfig.ALLOW_RENTPET && NpcConfig.LIST_PET_RENT_NPC.contains(getId())) {
            html.replace("_Quest", "_RentPet\">Rent Pet</a><br><a action=\"bypass -h Script");
        }

        html.replace("%objectId%", String.valueOf(getObjectId()));
        html.replace("%npcname%", getName());
        player.sendPacket(html);
    }

    @Override
    public void onBypassFeedback(Player player, String command) {
        if (command.startsWith("exchange")) {
            final String[] params = command.split(" ");
            final int val = Integer.parseInt(params[1]);
            switch (val) {
                case 1: {
                    exchange(player, 7585, 6650);
                    break;
                }
                case 2: {
                    exchange(player, 7583, 6648);
                    break;
                }
                case 3: {
                    exchange(player, 7584, 6649);
                    break;
                }
            }
        } else if (command.startsWith("evolve")) {
            final String[] params = command.split(" ");
            final int val = Integer.parseInt(params[1]);
            boolean ok = false;
            switch (val) {
                // Info evolve(player, "curent pet summon item", "new pet summon item", "level required to evolve")
                // To ignore evolve just put value 0 where do you like example: evolve(player, 0, 9882, 55);
                case 1: {
                    ok = doEvolve(player, this, 2375, 9882, 55);
                    break;
                }
                case 2: {
                    ok = doEvolve(player, this, 9882, 10426, 70);
                    break;
                }
                case 3: {
                    ok = doEvolve(player, this, 6648, 10311, 55);
                    break;
                }
                case 4: {
                    ok = doEvolve(player, this, 6650, 10313, 55);
                    break;
                }
                case 5: {
                    ok = doEvolve(player, this, 6649, 10312, 55);
                    break;
                }
            }

            if (!ok) {
                final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
                html.setFile(player, "data/html/petmanager/evolve_no.htm");
                player.sendPacket(html);
            }
        } else if (command.startsWith("restore")) {
            final String[] params = command.split(" ");
            final int val = Integer.parseInt(params[1]);
            boolean ok = false;
            switch (val) {
                // Info evolve(player, "curent pet summon item", "new pet summon item", "level required to evolve")
                case 1: {
                    ok = doRestore(player, this, 10307, 9882, 55);
                    break;
                }
                case 2: {
                    ok = doRestore(player, this, 10611, 10426, 70);
                    break;
                }
                case 3: {
                    ok = doRestore(player, this, 10308, 4422, 55);
                    break;
                }
                case 4: {
                    ok = doRestore(player, this, 10309, 4423, 55);
                    break;
                }
                case 5: {
                    ok = doRestore(player, this, 10310, 4424, 55);
                    break;
                }
            }

            if (!ok) {
                final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
                html.setFile(player, "data/html/petmanager/restore_no.htm");
                player.sendPacket(html);
            }
        } else {
            super.onBypassFeedback(player, command);
        }
    }

    public void exchange(Player player, int itemIdtake, int itemIdgive) {
        final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
        if (player.destroyItemByItemId(null, itemIdtake, 1, this, true)) {
            player.addItem(ItemProcessType.NONE, itemIdgive, 1, this, true);
            html.setFile(player, "data/html/petmanager/" + getId() + ".htm");
            player.sendPacket(html);
        } else {
            html.setFile(player, "data/html/petmanager/exchange_no.htm");
            player.sendPacket(html);
        }
    }

    public static boolean doEvolve(Player player, Npc npc, int itemIdtake, int itemIdgive, int petminLevel) {
        if ((itemIdtake == 0) || (itemIdgive == 0) || (petminLevel == 0)) {
            return false;
        }

        if (!player.hasPet()) {
            return false;
        }

        final Pet currentPet = player.getSummon().asPet();
        if (currentPet.isAlikeDead()) {
            PunishmentManager.handleIllegalPlayerAction(
                    player, player + " tried to use death pet exploit!", GeneralConfig.DEFAULT_PUNISH);
            return false;
        }

        Item item = null;
        final PetData oldData = PetDataTable.getInstance().getPetDataByItemId(itemIdtake);
        if (oldData == null) {
            return false;
        }

        final int oldnpcID = oldData.getNpcId();
        if ((currentPet.getStat().getLevel() < petminLevel) || (currentPet.getId() != oldnpcID)) {
            return false;
        }

        final PetData petData = PetDataTable.getInstance().getPetDataByItemId(itemIdgive);
        if (petData == null) {
            return false;
        }

        final int npcID = petData.getNpcId();
        if (npcID == 0) {
            return false;
        }

        long petexp = currentPet.getStat().getExp();
        final String oldname = currentPet.getName();
        final int oldX = currentPet.getX();
        final int oldY = currentPet.getY();
        final int oldZ = currentPet.getZ();
        final NpcTemplate npcTemplate = NpcData.getInstance().getTemplate(npcID);
        currentPet.unSummon(player);

        // Deleting old pet item.
        currentPet.destroyControlItem(player, true);
        item = player.getInventory().addItem(ItemProcessType.RESTORE, itemIdgive, 1, player, npc);

        // Summoning new pet
        final Pet petSummon = Pet.spawnPet(npcTemplate, player, item);
        if (petSummon == null) {
            return false;
        }

        // Fix for non-linear baby pet exp.
        final long minimumexp = petSummon.getStat().getExpForLevel(petminLevel);
        if (petexp < minimumexp) {
            petexp = minimumexp;
        }

        petSummon.getStat().addExp(petexp);
        petSummon.setCurrentHp(petSummon.getMaxHp());
        petSummon.setCurrentMp(petSummon.getMaxMp());
        petSummon.setCurrentFed(petSummon.getMaxFed());
        petSummon.setTitle(player.getName());
        petSummon.setName(oldname);
        petSummon.setRunning();
        petSummon.storeMe();

        player.setPet(petSummon);

        player.sendPacket(new MagicSkillUse(npc, 2046, 1, 1000, 600000));
        player.sendPacket(SystemMessageId.SUMMONING_YOUR_PET);

        // World.storeObject(petSummon);
        petSummon.spawnMe(oldX, oldY, oldZ);
        petSummon.startFeed();
        item.setEnchantLevel(petSummon.getLevel());

        ThreadPool.schedule(new EvolveFinalizer(player, petSummon), 900);
        if (petSummon.getCurrentFed() <= 0) {
            ThreadPool.schedule(new EvolveFeedWait(player, petSummon), 60000);
        } else {
            petSummon.startFeed();
        }

        return true;
    }

    public static boolean doRestore(Player player, Npc npc, int itemIdtake, int itemIdgive, int petminLevel) {
        if ((itemIdtake == 0) || (itemIdgive == 0) || (petminLevel == 0)) {
            return false;
        }

        final Item item = player.getInventory().getItemByItemId(itemIdtake);
        if (item == null) {
            return false;
        }

        final PetData oldData = PetDataTable.getInstance().getPetDataByItemId(itemIdtake);
        if (oldData == null) {
            return false;
        }

        final PetData petData = PetDataTable.getInstance().getPetDataByItemId(itemIdgive);
        if (petData == null) {
            return false;
        }

        final int npcId = petData.getNpcId();
        if (npcId == 0) {
            return false;
        }

        final int oldpetlvl = item.getEnchantLevel() < petminLevel ? petminLevel : item.getEnchantLevel();
        final NpcTemplate npcTemplate = NpcData.getInstance().getTemplate(npcId);

        // Deleting old pet item.
        final Item removedItem = player.getInventory().destroyItem(ItemProcessType.RESTORE, item, player, npc);
        final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
        sm.addItemName(removedItem);
        player.sendPacket(sm);

        // Give new pet item.
        final Item addedItem = player.getInventory().addItem(ItemProcessType.RESTORE, itemIdgive, 1, player, npc);

        // Summoning new pet
        final Pet petSummon = Pet.spawnPet(npcTemplate, player, addedItem);
        if (petSummon == null) {
            return false;
        }

        final long maxexp = petSummon.getStat().getExpForLevel(oldpetlvl);
        petSummon.getStat().addExp(maxexp);
        petSummon.setCurrentHp(petSummon.getMaxHp());
        petSummon.setCurrentMp(petSummon.getMaxMp());
        petSummon.setCurrentFed(petSummon.getMaxFed());
        petSummon.setTitle(player.getName());
        petSummon.setRunning();
        petSummon.storeMe();

        player.setPet(petSummon);

        player.sendPacket(new MagicSkillUse(npc, 2046, 1, 1000, 600000));
        player.sendPacket(SystemMessageId.SUMMONING_YOUR_PET);

        // World.storeObject(petSummon);
        petSummon.spawnMe(player.getX(), player.getY(), player.getZ());
        petSummon.startFeed();
        addedItem.setEnchantLevel(petSummon.getLevel());

        // Inventory update
        final InventoryUpdate iu = new InventoryUpdate();
        iu.addRemovedItem(removedItem);
        player.sendInventoryUpdate(iu);

        final StatusUpdate su = new StatusUpdate(player);
        su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
        player.sendPacket(su);

        player.broadcastUserInfo();

        World.removeObject(removedItem);

        ThreadPool.schedule(new EvolveFinalizer(player, petSummon), 900);
        if (petSummon.getCurrentFed() <= 0) {
            ThreadPool.schedule(new EvolveFeedWait(player, petSummon), 60000);
        } else {
            petSummon.startFeed();
        }

        return true;
    }

    @Slf4j
    public static class EvolveFeedWait implements Runnable {
        private final Player player;
        private final Pet petSummon;

        EvolveFeedWait(Player player, Pet petSummon) {
            this.player = player;
            this.petSummon = petSummon;
        }

        @Override
        public void run() {
            try {
                if (petSummon.getCurrentFed() <= 0) {
                    petSummon.unSummon(player);
                } else {
                    petSummon.startFeed();
                }
            } catch (Exception e) {
                log.warn("run: ", e);
            }
        }
    }

    @Slf4j
    public static class EvolveFinalizer implements Runnable {
        private final Player player;
        private final Pet petSummon;

        EvolveFinalizer(Player player, Pet petSummon) {
            this.player = player;
            this.petSummon = petSummon;
        }

        @Override
        public void run() {
            try {
                player.sendPacket(new MagicSkillLaunched(player, 2046, 1));
                petSummon.setFollowStatus(true);
                petSummon.setShowSummonAnimation(false);
            } catch (Throwable e) {
                log.warn("run: ", e);
            }
        }
    }
}
