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
package app.l2nx.highfive.entity.item;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemGrade;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.holders.ExtractableProduct;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.ActionType;
import app.l2nx.highfive.entity.item.type.CrystalType;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.entity.item.type.ItemType;
import app.l2nx.highfive.entity.item.type.MaterialType;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;
import app.l2nx.highfive.mechanics.stats.functions.FuncTemplate;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class contains all informations concerning the item (weapon, armor, etc).<br>
 * Mother class of :
 * <ul>
 * <li>Armor</li>
 * <li>EtcItem</li>
 * <li>Weapon</li>
 * </ul>
 */
@Slf4j
public abstract class ItemTemplate extends ListenersContainer {

    public static final int TYPE1_WEAPON_RING_EARRING_NECKLACE = 0;
    public static final int TYPE1_SHIELD_ARMOR = 1;
    public static final int TYPE1_ITEM_QUESTITEM_ADENA = 4;

    public static final int TYPE2_WEAPON = 0;
    public static final int TYPE2_SHIELD_ARMOR = 1;
    public static final int TYPE2_ACCESSORY = 2;
    public static final int TYPE2_QUEST = 3;
    public static final int TYPE2_MONEY = 4;
    public static final int TYPE2_OTHER = 5;

    @Getter
    private int id;

    @Getter
    private int displayId;

    @Getter
    private String name;

    @Getter
    private String icon;

    @Getter
    private int weight;

    @Getter
    private boolean stackable;

    @Getter
    private MaterialType materialType;

    @Getter
    private CrystalType crystalType;

    @Getter
    private int equipReuseDelay;

    @Getter
    private int duration;

    @Getter
    private long time;

    @Getter
    private int autoDestroyTime;

    @Getter
    private BodyPart bodyPart;

    @Getter
    private int referencePrice;

    @Getter
    private int crystalCount;

    @Getter
    private boolean sellable;

    @Getter
    private boolean dropable;

    @Getter
    private boolean destroyable;

    @Getter
    private boolean tradeable;

    @Getter
    private boolean depositable;

    private boolean enchantable;

    @Getter
    private boolean elementable;

    @Getter
    private boolean questItem;

    @Getter
    private boolean freightable;

    @Getter
    private boolean allowSelfResurrection;

    private boolean olyRestricted;

    @Getter
    private boolean forNpc;

    @Getter
    private boolean common;

    @Getter
    private boolean heroItem;

    @Getter
    private boolean pvpItem;

    private boolean immediateEffect;
    private boolean exImmediateEffect;

    @Getter
    private int defaultEnchantLevel;

    @Getter
    private ActionType defaultAction;

    @Getter
    protected int type1; // Needed for item list (inventory).

    @Getter
    protected int type2; // Different lists for armor, weapon, etc.

    @Getter
    protected Elementals[] elementals = null;

    protected List<FuncTemplate> funcTemplates;
    protected List<Condition> preConditions;
    private SkillHolder[] skills;
    private SkillHolder unequipSkill = null;

    private int useSkillDisTime;

    @Getter
    protected int reuseDelay;

    @Getter
    private int sharedReuseGroup;

    /**
     * Constructor of the Item that fill class variables.
     * @param set : StatSet corresponding to a set of couples (key,value) for description of the item
     */
    protected ItemTemplate(StatSet set) {
        set(set);
    }

    public void set(StatSet set) {
        this.id = set.getInt("item_id");
        displayId = set.getInt("displayId", this.id);
        name = set.getString("name");
        icon = set.getString("icon", null);
        weight = set.getInt("weight", 0);
        materialType = set.getEnum("material", MaterialType.class, MaterialType.STEEL);
        equipReuseDelay = set.getInt("equip_reuse_delay", 0) * 1000;
        duration = set.getInt("duration", -1);
        time = set.getInt("time", -1);
        autoDestroyTime = set.getInt("auto_destroy_time", -1) * 1000;
        bodyPart = BodyPart.fromName(set.getString("bodypart", "none"));
        referencePrice = set.getInt("price", 0);
        crystalType = set.getEnum("crystal_type", CrystalType.class, CrystalType.NONE);
        crystalCount = set.getInt("crystal_count", 0);
        stackable = set.getBoolean("is_stackable", false);
        sellable = set.getBoolean("is_sellable", true);
        dropable = set.getBoolean("is_dropable", true);
        destroyable = set.getBoolean("is_destroyable", true);
        tradeable = set.getBoolean("is_tradable", true);
        questItem = set.getBoolean("is_questitem", false);
        depositable = set.getBoolean("is_depositable", true);
        elementable = set.getBoolean("element_enabled", false);
        enchantable = set.getBoolean("enchant_enabled", false);
        freightable = set.getBoolean("is_freightable", false);
        allowSelfResurrection = set.getBoolean("allow_self_resurrection", false);
        olyRestricted = set.getBoolean("is_oly_restricted", false);
        forNpc = set.getBoolean("for_npc", false);
        immediateEffect = set.getBoolean("immediate_effect", false);
        exImmediateEffect = set.getBoolean("ex_immediate_effect", false);
        defaultAction = set.getEnum("default_action", ActionType.class, ActionType.NONE);
        useSkillDisTime = set.getInt("useSkillDisTime", 0);
        defaultEnchantLevel = set.getInt("enchanted", 0);
        reuseDelay = set.getInt("reuse_delay", 0);
        sharedReuseGroup = set.getInt("shared_reuse_group", 0);

        String skills = set.getString("item_skill", null);
        if (skills != null) {
            final String[] skillsSplit = skills.split(";");
            this.skills = new SkillHolder[skillsSplit.length];
            int used = 0;
            for (String element : skillsSplit) {
                try {
                    final String[] skillSplit = element.split("-");
                    final int id = Integer.parseInt(skillSplit[0]);
                    if (id == 0) {
                        log.info("Ignoring item_skill({}) for item {}. Skill id is 0!", element, this);
                        continue;
                    }

                    final int level = Integer.parseInt(skillSplit[1]);
                    if (level == 0) {
                        log.info("Ignoring item_skill({}) for item {}. Skill level is 0!", element, this);
                        continue;
                    }

                    this.skills[used] = new SkillHolder(id, level);
                    ++used;
                } catch (Exception e) {
                    log.warn(
                            "Failed to parse item_skill({}) for item {}! Format: SkillId0-SkillLevel0[;SkillIdN-SkillLevelN]",
                            element,
                            this);
                }
            }

            // this is only loading? just don't leave a null or use a collection?
            if (used != this.skills.length) {
                final SkillHolder[] skillHolder = new SkillHolder[used];
                System.arraycopy(this.skills, 0, skillHolder, 0, used);
                this.skills = skillHolder;
            }
        }

        skills = set.getString("unequip_skill", null);
        if (skills != null) {
            final String[] info = skills.split("-");
            if ((info != null) && (info.length == 2)) {
                int id = 0;
                int level = 0;
                try {
                    id = Integer.parseInt(info[0]);
                    level = Integer.parseInt(info[1]);
                } catch (Exception nfe) {
                    // Incorrect syntax, don't add new skill.
                    log.info("Could not parse {} in weapon unequip skills! item {}", skills, this);
                }

                if ((id > 0) && (level > 0)) {
                    unequipSkill = new SkillHolder(id, level);
                }
            }
        }

        common = ((this.id >= 11605) && (this.id <= 12361));
        heroItem = ((this.id >= 6611) && (this.id <= 6621))
                || ((this.id >= 9388) && (this.id <= 9390))
                || (this.id == 6842);
        pvpItem = ((this.id >= 10667) && (this.id <= 10835))
                || ((this.id >= 12852) && (this.id <= 12977))
                || ((this.id >= 14363) && (this.id <= 14525))
                || (this.id == 14528)
                || (this.id == 14529)
                || (this.id == 14558)
                || ((this.id >= 15913) && (this.id <= 16024))
                || ((this.id >= 16134) && (this.id <= 16147))
                || (this.id == 16149)
                || (this.id == 16151)
                || (this.id == 16153)
                || (this.id == 16155)
                || (this.id == 16157)
                || (this.id == 16159)
                || ((this.id >= 16168) && (this.id <= 16176))
                || ((this.id >= 16179) && (this.id <= 16220))
                || ((this.id >= 21932) && (this.id <= 21934))
                || (this.id == 21938)
                || ((this.id >= 21952) && (this.id <= 21954))
                || (this.id == 21958)
                || ((this.id >= 21970) && (this.id <= 21972));
    }

    /**
     * Returns the itemType.
     * @return Enum
     */
    public abstract ItemType getItemType();

    /**
     * Verifies if the item is an etc item.
     * @return {@code true} if the item is an etc item, {@code false} otherwise.
     */
    public boolean isEtcItem() {
        return false;
    }

    /**
     * Verifies if the item is an armor.
     * @return {@code true} if the item is an armor, {@code false} otherwise.
     */
    public boolean isArmor() {
        return false;
    }

    /**
     * Verifies if the item is a weapon.
     * @return {@code true} if the item is a weapon, {@code false} otherwise.
     */
    public boolean isWeapon() {
        return false;
    }

    /**
     * Verifies if the item is a magic weapon.
     * @return {@code true} if the weapon is magic, {@code false} otherwise.
     */
    public boolean isMagicWeapon() {
        return false;
    }

    public abstract int getItemMask();

    /**
     * Returns if the item is crystallizable
     * @return boolean
     */
    public boolean isCrystallizable() {
        return (crystalType != CrystalType.NONE) && (crystalCount > 0);
    }

    /**
     * @return return General item grade (No S80, S84)
     */
    public ItemGrade getItemGrade() {
        return ItemGrade.valueOf(crystalType);
    }

    /**
     * Return the ID of crystal if item is crystallizable
     * @return int
     */
    public int getCrystalItemId() {
        return crystalType.getCrystalId();
    }

    /**
     * For grades S80 and S84 return S
     * @return the grade of the item.
     */
    public CrystalType getCrystalTypePlus() {
        switch (crystalType) {
            case S80:
            case S84: {
                return CrystalType.S;
            }
            default: {
                return crystalType;
            }
        }
    }

    /**
     * @param enchantLevel
     * @return the quantity of crystals for crystallization on specific enchant level
     */
    public int getCrystalCount(int enchantLevel) {
        if (enchantLevel > 3) {
            switch (type2) {
                case TYPE2_SHIELD_ARMOR:
                case TYPE2_ACCESSORY: {
                    return crystalCount + (crystalType.getCrystalEnchantBonusArmor() * ((3 * enchantLevel) - 6));
                }
                case TYPE2_WEAPON: {
                    return crystalCount + (crystalType.getCrystalEnchantBonusWeapon() * ((2 * enchantLevel) - 3));
                }
                default: {
                    return crystalCount;
                }
            }
        }

        if (enchantLevel <= 0) {
            return crystalCount;
        }

        switch (type2) {
            case TYPE2_SHIELD_ARMOR:
            case TYPE2_ACCESSORY: {
                return crystalCount + (crystalType.getCrystalEnchantBonusArmor() * enchantLevel);
            }
            case TYPE2_WEAPON: {
                return crystalCount + (crystalType.getCrystalEnchantBonusWeapon() * enchantLevel);
            }
            default: {
                return crystalCount;
            }
        }
    }

    public Elementals getElemental(byte attribute) {
        for (Elementals elm : elementals) {
            if (elm.getElement() == attribute) {
                return elm;
            }
        }

        return null;
    }

    /**
     * Sets the base elemental of the item.
     * @param element the element to set.
     */
    public void setElementals(Elementals element) {
        if (elementals == null) {
            elementals = new Elementals[1];
            elementals[0] = element;
        } else {
            Elementals elm = getElemental(element.getElement());
            if (elm != null) {
                elm.setValue(element.getValue());
            } else {
                elm = element;
                final Elementals[] array = new Elementals[elementals.length + 1];
                System.arraycopy(elementals, 0, array, 0, elementals.length);
                array[elementals.length] = elm;
                elementals = array;
            }
        }
    }

    /**
     * @return {@code true} if the item can be equipped, {@code false} otherwise.
     */
    public boolean isEquipable() {
        return (bodyPart != BodyPart.NONE) && !(getItemType() instanceof EtcItemType);
    }

    /**
     * This method also check the enchant blacklist.
     * @return {@code true} if the item can be enchanted, {@code false} otherwise.
     */
    public boolean isEnchantable() {
        return (Arrays.binarySearch(PlayerConfig.ENCHANT_BLACKLIST, id) < 0) && enchantable;
    }

    public boolean isPotion() {
        return getItemType() == EtcItemType.POTION;
    }

    public boolean isElixir() {
        return getItemType() == EtcItemType.ELIXIR;
    }

    public boolean isScroll() {
        return getItemType() == EtcItemType.SCROLL;
    }

    /**
     * Get the functions used by this item.
     * @param item : Item pointing out the item
     * @param creature : Creature pointing out the player
     * @return the list of functions
     */
    public List<AbstractFunction> getStatFuncs(Item item, Creature creature) {
        if ((funcTemplates == null) || funcTemplates.isEmpty()) {
            return Collections.<AbstractFunction>emptyList();
        }

        final List<AbstractFunction> funcs = new ArrayList<>(funcTemplates.size());
        for (FuncTemplate t : funcTemplates) {
            final AbstractFunction f = t.getFunc(creature, creature, item, item);
            if (f != null) {
                funcs.add(f);
            }
        }

        return funcs;
    }

    /**
     * Checks if a function of the specified class is already attached to this item.
     * @param functionClass the class of the function to search for
     * @return {@code true} if a function of the specified class exists, {@code false} otherwise
     */
    public boolean hasFunction(Class<? extends AbstractFunction> functionClass) {
        if ((funcTemplates == null) || funcTemplates.isEmpty()) {
            return false;
        }

        for (FuncTemplate template : funcTemplates) {
            if (template.getClass().equals(functionClass)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Add the FuncTemplate f to the list of functions used with the item
     * @param f : FuncTemplate to add
     */
    public void attach(FuncTemplate f) {
        switch (f.getStat()) {
            case FIRE_RES:
            case FIRE_POWER: {
                setElementals(new Elementals(Elementals.FIRE, (int) f.getValue()));
                break;
            }
            case WATER_RES:
            case WATER_POWER: {
                setElementals(new Elementals(Elementals.WATER, (int) f.getValue()));
                break;
            }
            case WIND_RES:
            case WIND_POWER: {
                setElementals(new Elementals(Elementals.WIND, (int) f.getValue()));
                break;
            }
            case EARTH_RES:
            case EARTH_POWER: {
                setElementals(new Elementals(Elementals.EARTH, (int) f.getValue()));
                break;
            }
            case HOLY_RES:
            case HOLY_POWER: {
                setElementals(new Elementals(Elementals.HOLY, (int) f.getValue()));
                break;
            }
            case DARK_RES:
            case DARK_POWER: {
                setElementals(new Elementals(Elementals.DARK, (int) f.getValue()));
                break;
            }
        }

        if (funcTemplates == null) {
            funcTemplates = new ArrayList<>(1);
        }

        funcTemplates.add(f);
    }

    public void attach(Condition c) {
        if (preConditions == null) {
            preConditions = new ArrayList<>(1);
        }

        if (!preConditions.contains(c)) {
            preConditions.add(c);
        }
    }

    public boolean hasSkills() {
        return skills != null;
    }

    /**
     * Method to retrieve skills linked to this item armor and weapon: passive skills etcitem: skills used on item use <-- ???
     * @return Skills linked to this item as SkillHolder[]
     */
    public SkillHolder[] getSkills() {
        return skills;
    }

    /**
     * @return skill that activates, when player unequip this weapon or armor
     */
    public Skill getUnequipSkill() {
        return unequipSkill == null ? null : unequipSkill.getSkill();
    }

    public boolean checkCondition(Creature creature, WorldObject object, boolean sendMessage) {
        if (creature.isGM() && !GeneralConfig.GM_ITEM_RESTRICTION) {
            return true;
        }

        // Don't allow hero equipment and restricted items during Olympiad.
        if ((isOlyRestrictedItem() || heroItem)
                && creature.isPlayer()
                && creature.asPlayer().isInOlympiadMode()) {
            if (isEquipable()) {
                creature.sendPacket(SystemMessageId.YOU_CANNOT_EQUIP_THAT_ITEM_IN_A_GRAND_OLYMPIAD_MATCH);
            } else {
                creature.sendPacket(SystemMessageId.YOU_CANNOT_USE_THAT_ITEM_IN_A_GRAND_OLYMPIAD_MATCH);
            }

            return false;
        }

        if (!isConditionAttached()) {
            return true;
        }

        final Creature target = object.isCreature() ? object.asCreature() : null;
        for (Condition preCondition : preConditions) {
            if (preCondition == null) {
                continue;
            }

            if (!preCondition.test(creature, target, null, null)) {
                if (creature.isSummon()) {
                    creature.sendPacket(SystemMessageId.THIS_PET_CANNOT_USE_THIS_ITEM);
                    return false;
                }

                if (sendMessage) {
                    final String msg = preCondition.getMessage();
                    final int msgId = preCondition.getMessageId();
                    if (msg != null) {
                        creature.sendMessage(msg);
                    } else if (msgId != 0) {
                        final SystemMessage sm = new SystemMessage(msgId);
                        if (preCondition.isAddName()) {
                            sm.addItemName(id);
                        }

                        creature.sendPacket(sm);
                    }
                }

                return false;
            }
        }

        return true;
    }

    public boolean isConditionAttached() {
        return (preConditions != null) && !preConditions.isEmpty();
    }

    public boolean isOlyRestrictedItem() {
        return olyRestricted || OlympiadConfig.LIST_OLY_RESTRICTED_ITEMS.contains(id);
    }

    /**
     * Verifies if the item has effects immediately.<br>
     * <i>Used for herbs mostly.</i>
     * @return {@code true} if the item applies effects immediately, {@code false} otherwise
     */
    public boolean hasExImmediateEffect() {
        return exImmediateEffect;
    }

    /**
     * Verifies if the item has effects immediately.
     * @return {@code true} if the item applies effects immediately, {@code false} otherwise
     */
    public boolean hasImmediateEffect() {
        return immediateEffect;
    }

    public int useSkillDisTime() {
        return useSkillDisTime;
    }

    public boolean isPetItem() {
        return getItemType() == EtcItemType.PET_COLLAR;
    }

    /**
     * @param extractableProduct
     */
    public void addCapsuledItem(ExtractableProduct extractableProduct) {}

    public Skill getEnchant4Skill() {
        return null;
    }

    /**
     * Returns the name of the item followed by the item ID.
     * @return the name and the ID of the item
     */
    @Override
    public String toString() {
        return name + "(" + id + ")";
    }
}
