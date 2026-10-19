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
package app.l2nx.highfive.entity.actor.enums.creature;

import lombok.Getter;

public enum InstanceType {
    WorldObject(null),
    Item(WorldObject),
    Creature(WorldObject),
    Npc(Creature),
    Playable(Creature),
    Summon(Playable),
    Player(Playable),
    Folk(Npc),
    Merchant(Folk),
    Warehouse(Folk),
    StaticObject(Creature),
    Door(Creature),
    TerrainObject(Npc),
    EffectPoint(Npc),

    // Summons, Pets, Decoys and Traps
    Servitor(Summon),
    Pet(Summon),
    BabyPet(Pet),
    Decoy(Creature),
    Trap(Npc),

    // Attackable
    Attackable(Npc),
    Guard(Attackable),
    QuestGuard(Guard),
    Monster(Attackable),
    Chest(Monster),
    FeedableBeast(Monster),
    TamedBeast(FeedableBeast),
    FriendlyMob(Attackable),
    RiftInvader(Monster),
    RaidBoss(Monster),
    GrandBoss(RaidBoss),

    // FlyMobs
    FlyNpc(Folk),
    FlyMonster(Monster),
    FlyRaidBoss(RaidBoss),
    FlyTerrainObject(Npc),

    // Festival
    FestivalGuide(Npc),
    FestivalMonster(Monster),

    // Vehicles
    Vehicle(Creature),
    Boat(Vehicle),
    AirShip(Vehicle),
    ControllableAirShip(AirShip),

    // Siege
    Defender(Attackable),
    Artefact(Folk),
    ControlTower(Npc),
    FlameTower(Npc),
    SiegeFlag(Npc),

    // Fort Siege
    FortCommander(Defender),

    // Fort NPCs
    FortLogistics(Merchant),
    FortManager(Merchant),

    // Seven Signs
    SignsPriest(Npc),
    DawnPriest(SignsPriest),
    DuskPriest(SignsPriest),
    DungeonGatekeeper(Npc),

    // City NPCs
    Adventurer(Folk),
    Auctioneer(Npc),
    BroadcastingTower(Npc),
    ClanHallManager(Merchant),
    Fisherman(Merchant),
    OlympiadManager(Npc),
    PetManager(Merchant),
    RaceManager(Npc),
    Teleporter(Npc),
    Trainer(Folk),
    VillageMaster(Folk),

    // Doormen
    Doorman(Folk),
    CastleDoorman(Doorman),
    FortDoorman(Doorman),
    ClanHallDoorman(Doorman),

    // Custom
    ClassMaster(Folk),
    SchemeBuffer(Npc),
    EventMob(Npc),
    UCManagerInstance(Folk);

    @Getter
    private final InstanceType parent;

    private final long typeL;
    private final long typeH;
    private final long maskL;
    private final long maskH;

    private InstanceType(InstanceType parent) {
        this.parent = parent;

        final int high = ordinal() - (Long.SIZE - 1);
        if (high < 0) {
            typeL = 1L << ordinal();
            typeH = 0;
        } else {
            typeL = 0;
            typeH = 1L << high;
        }

        if ((typeL < 0) || (typeH < 0)) {
            throw new Error("Too many instance types, failed to load " + name());
        }

        if (parent != null) {
            maskL = typeL | parent.maskL;
            maskH = typeH | parent.maskH;
        } else {
            maskL = typeL;
            maskH = typeH;
        }
    }

    public boolean isType(InstanceType it) {
        return ((maskL & it.typeL) > 0) || ((maskH & it.typeH) > 0);
    }

    public boolean isTypes(InstanceType... it) {
        for (InstanceType i : it) {
            if (isType(i)) {
                return true;
            }
        }

        return false;
    }
}
