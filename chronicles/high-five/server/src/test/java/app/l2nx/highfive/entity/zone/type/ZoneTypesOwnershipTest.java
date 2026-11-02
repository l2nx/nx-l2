/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone.type;

import static app.l2nx.highfive.entity.zone.ZoneTestObjects.player;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.mechanics.siege.Siegable;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ZoneTypesOwnershipTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("ownerCheckedCalls")
    <T> void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Supplier<T> zone, Consumer<T> call) {
        final T built = WorldLoopExtension.callInPass(zone);

        assertThatThrownBy(() -> call.accept(built))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("touched from thread");
    }

    static Stream<Arguments> ownerCheckedCalls() {
        return Stream.of(
                call("SiegeZone.setActive", SiegeZone::new, (SiegeZone z) -> z.setActive(true)),
                call("SiegeZone.isActive", SiegeZone::new, SiegeZone::isActive),
                call("SiegeZone.getSiegeObjectId", SiegeZone::new, SiegeZone::getSiegeObjectId),
                call(
                        "SiegeZone.setSiegeInstance",
                        SiegeZone::new,
                        (SiegeZone z) -> z.setSiegeInstance(mock(Siegable.class))),
                call("SiegeZone.getSettings", SiegeZone::new, SiegeZone::getSettings),
                call("SiegeZone.announceToPlayers", SiegeZone::new, (SiegeZone z) -> z.announceToPlayers("Siege")),
                call("SiegeZone.banishForeigners", SiegeZone::new, (SiegeZone z) -> z.banishForeigners(1)),
                call(
                        "SiegeZone.updateZoneStatusForCharactersInside",
                        SiegeZone::new,
                        SiegeZone::updateZoneStatusForCharactersInside),
                call("SiegeZone.onDieInside", SiegeZone::new, (SiegeZone z) -> z.onDieInside(player(1))),
                call(
                        "SiegeZone.onPlayerLogoutInside",
                        SiegeZone::new,
                        (SiegeZone z) -> z.onPlayerLogoutInside(player(1))),
                call("EffectZone.addSkill", EffectZone::new, (EffectZone z) -> z.addSkill(4515, 1)),
                call("EffectZone.removeSkill", EffectZone::new, (EffectZone z) -> z.removeSkill(4515)),
                call("EffectZone.clearSkills", EffectZone::new, EffectZone::clearSkills),
                call("EffectZone.getSkillLevel", EffectZone::new, (EffectZone z) -> z.getSkillLevel(4515)),
                call("EffectZone.getChance (accessor)", EffectZone::new, EffectZone::getChance),
                call("EffectZone.setParameter", EffectZone::new, (EffectZone z) -> z.setParameter("chance", "50")),
                call("DamageZone.setParameter", DamageZone::new, (DamageZone z) -> z.setParameter("dmgHPSec", "10")),
                call(
                        "OlympiadStadiumZone.registerTask",
                        OlympiadStadiumZone::new,
                        (OlympiadStadiumZone z) -> z.registerTask(null)),
                call(
                        "OlympiadStadiumZone.getSpectatorSpawns (accessor)",
                        OlympiadStadiumZone::new,
                        OlympiadStadiumZone::getSpectatorSpawns),
                call(
                        "OlympiadStadiumZone.broadcastPacket",
                        OlympiadStadiumZone::new,
                        (OlympiadStadiumZone z) -> z.broadcastPacket(mock(ServerPacket.class))),
                call(
                        "OlympiadStadiumZone.broadcastPacketToObservers",
                        OlympiadStadiumZone::new,
                        (OlympiadStadiumZone z) -> z.broadcastPacketToObservers(mock(ServerPacket.class))),
                call(
                        "OlympiadStadiumZone.parseLoc",
                        OlympiadStadiumZone::new,
                        (OlympiadStadiumZone z) -> z.parseLoc(1, 2, 3, "spectatorSpawn")),
                call("ZoneRespawn.getSpawns (accessor)", ResidenceTeleportZone::new, ResidenceTeleportZone::getSpawns),
                call(
                        "ZoneRespawn.addSpawn",
                        ResidenceTeleportZone::new,
                        (ResidenceTeleportZone z) -> z.addSpawn(1, 2, 3)),
                call("ZoneRespawn.getSpawnLoc", ResidenceTeleportZone::new, ResidenceTeleportZone::getSpawnLoc),
                call(
                        "ResidenceHallTeleportZone.checkTeleportTask",
                        ResidenceHallTeleportZone::new,
                        ResidenceHallTeleportZone::checkTeleportTask),
                call(
                        "ResidenceHallTeleportZone.getResidenceZoneId (accessor)",
                        ResidenceHallTeleportZone::new,
                        ResidenceHallTeleportZone::getResidenceZoneId),
                call("NoRestartZone.setRestartTime", NoRestartZone::new, (NoRestartZone z) -> z.setRestartTime(1)),
                call("NoRestartZone.getRestartTime (accessor)", NoRestartZone::new, NoRestartZone::getRestartTime),
                call(
                        "NoRestartZone.onPlayerLoginInside",
                        NoRestartZone::new,
                        (NoRestartZone z) -> z.onPlayerLoginInside(player(1))),
                call("RespawnZone.getAllRespawnPoints (accessor)", RespawnZone::new, RespawnZone::getAllRespawnPoints),
                call(
                        "RespawnZone.addRaceRespawnPoint",
                        RespawnZone::new,
                        (RespawnZone z) -> z.addRaceRespawnPoint("HUMAN", "TalkingIsland")),
                call("TownZone.getTownId (accessor)", TownZone::new, TownZone::getTownId),
                call("MotherTreeZone.getHpRegenBonus (accessor)", MotherTreeZone::new, MotherTreeZone::getHpRegenBonus),
                call("SwampZone.getMoveBonus (accessor)", SwampZone::new, SwampZone::getMoveBonus),
                call(
                        "SiegableHallZone.getChallengerSpawns (accessor)",
                        SiegableHallZone::new,
                        SiegableHallZone::getChallengerSpawns),
                call("JailZone.getLocationIn", JailZone::new, (JailZone z) -> JailZone.getLocationIn()));
    }

    private static <T> Arguments call(String name, IntFunction<T> zone, Consumer<T> call) {
        return Arguments.of(Named.of(name, (Supplier<T>) () -> zone.apply(70_301)), call);
    }
}
