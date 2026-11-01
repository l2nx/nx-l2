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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.data.xml.RecipeData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.WarehouseItem;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.recipe.RecipeList;
import app.l2nx.highfive.entity.item.type.CrystalType;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.entity.item.type.MaterialType;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.enums.WarehouseListType;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SortedWareHouseWithdrawalList extends ServerPacket {
    public static final int PRIVATE = 1;
    public static final int CLAN = 2;
    public static final int CASTLE = 3;
    public static final int FREIGHT = 4;

    public static final byte A2Z = 1;
    public static final byte Z2A = -1;
    public static final byte GRADE = 2;
    public static final byte LEVEL = 3;
    public static final byte TYPE = 4;
    public static final byte WEAR = 5;
    public static final int MAX_SORT_LIST_ITEMS = 300;

    private long playerAdena;
    private List<WarehouseItem> objects = new ArrayList<>();
    private int whType;

    public SortedWareHouseWithdrawalList(Player player, int type, WarehouseListType itemtype, byte sortorder) {
        whType = type;
        playerAdena = player.getAdena();
        if (player.getActiveWarehouse() == null) {
            log.warn("Error while sending withdraw request to: {}", player.getName());
            return;
        }

        try (Snapshot<Item> accepted = ScratchBuffers.empty()) {
            player.getActiveWarehouse().collectItems(accepted, filterOf(itemtype));
            for (Item item : accepted) {
                if (objects.size() == MAX_SORT_LIST_ITEMS) {
                    break;
                }

                objects.add(new WarehouseItem(item));
            }
        }

        try {
            switch (sortorder) {
                case A2Z:
                case Z2A: {
                    objects.sort(new WarehouseItemNameComparator(sortorder));
                    break;
                }
                case GRADE: {
                    if ((itemtype == WarehouseListType.ARMOR) || (itemtype == WarehouseListType.WEAPON)) {
                        objects.sort(new WarehouseItemNameComparator(A2Z));
                        objects.sort(new WarehouseItemGradeComparator(A2Z));
                    }
                    break;
                }
                case LEVEL: {
                    if (itemtype == WarehouseListType.RECIPE) {
                        objects.sort(new WarehouseItemNameComparator(A2Z));
                        objects.sort(new WarehouseItemRecipeComparator(A2Z));
                    }
                    break;
                }
                case TYPE: {
                    if (itemtype == WarehouseListType.MATERIAL) {
                        objects.sort(new WarehouseItemNameComparator(A2Z));
                        objects.sort(new WarehouseItemTypeComparator(A2Z));
                    }
                    break;
                }
                case WEAR: {
                    if (itemtype == WarehouseListType.ARMOR) {
                        objects.sort(new WarehouseItemNameComparator(A2Z));
                        objects.sort(new WarehouseItemBodypartComparator(A2Z));
                    }
                    break;
                }
            }
        } catch (Exception e) {
        }
    }

    public static byte getOrder(String order) {
        if (order == null) {
            return A2Z;
        } else if (order.startsWith("A2Z")) {
            return A2Z;
        } else if (order.startsWith("Z2A")) {
            return Z2A;
        } else if (order.startsWith("GRADE")) {
            return GRADE;
        } else if (order.startsWith("TYPE")) {
            return TYPE;
        } else if (order.startsWith("WEAR")) {
            return WEAR;
        } else {
            try {
                return Byte.parseByte(order);
            } catch (NumberFormatException ex) {
                return A2Z;
            }
        }
    }

    private static class WarehouseItemNameComparator implements Comparator<WarehouseItem> {
        private byte order = 0;

        protected WarehouseItemNameComparator(byte sortOrder) {
            order = sortOrder;
        }

        @Override
        public int compare(WarehouseItem o1, WarehouseItem o2) {
            if ((o1.getType2() == ItemTemplate.TYPE2_MONEY) && (o2.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? Z2A : A2Z);
            }

            if ((o2.getType2() == ItemTemplate.TYPE2_MONEY) && (o1.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? A2Z : Z2A);
            }

            final String s1 = o1.getItemName();
            final String s2 = o2.getItemName();
            return (order == A2Z ? s1.compareTo(s2) : s2.compareTo(s1));
        }
    }

    private static class WarehouseItemRecipeComparator implements Comparator<WarehouseItem> {
        private int order = 0;
        private RecipeData rd = null;

        protected WarehouseItemRecipeComparator(int sortOrder) {
            order = sortOrder;
            rd = RecipeData.getInstance();
        }

        @Override
        public int compare(WarehouseItem o1, WarehouseItem o2) {
            if ((o1.getType2() == ItemTemplate.TYPE2_MONEY) && (o2.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? Z2A : A2Z);
            }

            if ((o2.getType2() == ItemTemplate.TYPE2_MONEY) && (o1.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? A2Z : Z2A);
            }

            if ((o1.isEtcItem() && (o1.getItemType() == EtcItemType.RECIPE))
                    && (o2.isEtcItem() && (o2.getItemType() == EtcItemType.RECIPE))) {
                try {
                    final RecipeList rp1 = rd.getRecipeByItemId(o1.getItemId());
                    if (rp1 == null) {
                        return (order == A2Z ? A2Z : Z2A);
                    }

                    final RecipeList rp2 = rd.getRecipeByItemId(o2.getItemId());
                    if (rp2 == null) {
                        return (order == A2Z ? Z2A : A2Z);
                    }

                    final Integer i1 = rp1.getLevel();
                    final Integer i2 = rp2.getLevel();
                    return (order == A2Z ? i1.compareTo(i2) : i2.compareTo(i1));
                } catch (Exception e) {
                    return 0;
                }
            }

            final String s1 = o1.getItemName();
            final String s2 = o2.getItemName();
            return (order == A2Z ? s1.compareTo(s2) : s2.compareTo(s1));
        }
    }

    private static class WarehouseItemBodypartComparator implements Comparator<WarehouseItem> {
        private byte order = 0;

        protected WarehouseItemBodypartComparator(byte sortOrder) {
            order = sortOrder;
        }

        @Override
        public int compare(WarehouseItem o1, WarehouseItem o2) {
            if ((o1.getType2() == ItemTemplate.TYPE2_MONEY) && (o2.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? Z2A : A2Z);
            }

            if ((o2.getType2() == ItemTemplate.TYPE2_MONEY) && (o1.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? A2Z : Z2A);
            }

            final Integer i1 = o1.getBodyPart().getMask();
            final Integer i2 = o2.getBodyPart().getMask();
            return (order == A2Z ? i1.compareTo(i2) : i2.compareTo(i1));
        }
    }

    private static class WarehouseItemGradeComparator implements Comparator<WarehouseItem> {
        byte order = 0;

        protected WarehouseItemGradeComparator(byte sortOrder) {
            order = sortOrder;
        }

        @Override
        public int compare(WarehouseItem o1, WarehouseItem o2) {
            if ((o1.getType2() == ItemTemplate.TYPE2_MONEY) && (o2.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? Z2A : A2Z);
            }

            if ((o2.getType2() == ItemTemplate.TYPE2_MONEY) && (o1.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? A2Z : Z2A);
            }

            final CrystalType i1 = o1.getItemGrade();
            final CrystalType i2 = o2.getItemGrade();
            return (order == A2Z ? i1.compareTo(i2) : i2.compareTo(i1));
        }
    }

    /** Only sorts well if the ETCITEM.TYPE column data is fixed. */
    private static class WarehouseItemTypeComparator implements Comparator<WarehouseItem> {
        byte order = 0;

        protected WarehouseItemTypeComparator(byte sortOrder) {
            order = sortOrder;
        }

        @Override
        public int compare(WarehouseItem o1, WarehouseItem o2) {
            if ((o1.getType2() == ItemTemplate.TYPE2_MONEY) && (o2.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? Z2A : A2Z);
            }

            if ((o2.getType2() == ItemTemplate.TYPE2_MONEY) && (o1.getType2() != ItemTemplate.TYPE2_MONEY)) {
                return (order == A2Z ? A2Z : Z2A);
            }

            try {
                final MaterialType i1 = o1.getItem().getMaterialType();
                final MaterialType i2 = o2.getItem().getMaterialType();
                return (order == A2Z ? i1.compareTo(i2) : i2.compareTo(i1));
            } catch (Exception e) {
                return 0;
            }
        }
    }

    private static Predicate<Item> filterOf(WarehouseListType type) {
        return switch (type) {
            case WEAPON ->
                orMoney(item -> item.isWeapon()
                        || (item.getTemplate().getType2() == ItemTemplate.TYPE2_WEAPON)
                        || (item.isEtcItem() && (item.getItemType() == EtcItemType.ARROW)));
            case ARMOR -> orMoney(Item::isArmor);
            case ETCITEM -> orMoney(Item::isEtcItem);
            case MATERIAL -> orMoney(item -> isEtcItemOf(item, EtcItemType.MATERIAL));
            case RECIPE -> orMoney(item -> isEtcItemOf(item, EtcItemType.RECIPE));
            case AMULETT -> orMoney(item -> item.isEtcItem() && isAmulet(item));
            case SPELLBOOK -> {
                final Set<Integer> bookIds = bookIds();
                yield orMoney(item -> item.isEtcItem() && bookIds.contains(item.getId()) && !isAmulet(item));
            }
            case CONSUMABLE ->
                orMoney(item -> isEtcItemOf(item, EtcItemType.SCROLL) || isEtcItemOf(item, EtcItemType.SHOT));
            case SHOT -> orMoney(item -> isEtcItemOf(item, EtcItemType.SHOT));
            case SCROLL -> orMoney(item -> isEtcItemOf(item, EtcItemType.SCROLL));
            case SEED -> orMoney(item -> isEtcItemOf(item, EtcItemType.SEED));
            case OTHER ->
                orMoney(item -> item.isEtcItem()
                        && (item.getEtcItem().getItemType() != EtcItemType.MATERIAL)
                        && (item.getEtcItem().getItemType() != EtcItemType.RECIPE)
                        && (item.getEtcItem().getItemType() != EtcItemType.SCROLL)
                        && (item.getEtcItem().getItemType() != EtcItemType.SHOT));
            default -> _ -> true;
        };
    }

    private static Predicate<Item> orMoney(Predicate<Item> kind) {
        return item -> (item.getTemplate().getType2() == ItemTemplate.TYPE2_MONEY) || kind.test(item);
    }

    private static boolean isEtcItemOf(Item item, EtcItemType type) {
        return item.isEtcItem() && (item.getEtcItem().getItemType() == type);
    }

    private static boolean isAmulet(Item item) {
        return item.getItemName().toUpperCase().startsWith("AMULET");
    }

    private static Set<Integer> bookIds() {
        final Set<Integer> bookIds = new HashSet<>();
        for (PlayerClass playerClass : PlayerClass.values()) {
            for (SkillLearn skillLearn : SkillTreeData.getInstance()
                    .getCompleteClassSkillTree(playerClass)
                    .values()) {
                for (ItemHolder requiredItem : skillLearn.getRequiredItems()) {
                    bookIds.add(requiredItem.getId());
                }
            }
        }

        return bookIds;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.WAREHOUSE_WITHDRAW_LIST.writeId(this, buffer);
        buffer.writeShort(whType);
        buffer.writeLong(playerAdena);
        buffer.writeShort(objects.size());
        for (WarehouseItem item : objects) {
            buffer.writeInt(item.getObjectId());
            buffer.writeInt(item.getItem().getDisplayId());
            buffer.writeInt(item.getLocationSlot());
            buffer.writeLong(item.getCount());
            buffer.writeShort(item.getItem().getType2());
            buffer.writeShort(item.getCustomType1());
            buffer.writeShort(0); // Can't be equipped in WH
            buffer.writeInt(item.getItem().getBodyPart().getMask());
            buffer.writeShort(item.getEnchantLevel());
            buffer.writeShort(item.getCustomType2());
            if (item.isAugmented()) {
                buffer.writeInt(item.getAugmentationId());
            } else {
                buffer.writeInt(0);
            }

            buffer.writeInt(item.getMana());
            buffer.writeInt(item.getTime());
            buffer.writeShort(item.getAttackElementType());
            buffer.writeShort(item.getAttackElementPower());
            for (byte i = 0; i < 6; i++) {
                buffer.writeShort(item.getElementDefAttr(i));
            }

            for (int op : item.getEnchantOptions()) {
                buffer.writeShort(op);
            }

            buffer.writeInt(item.getObjectId());
        }
    }
}
