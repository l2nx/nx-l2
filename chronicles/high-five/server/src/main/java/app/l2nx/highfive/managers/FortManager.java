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
package app.l2nx.highfive.managers;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.data.holders.FortTemplate;
import app.l2nx.highfive.data.xml.FortData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.repository.residence.FortDoorUpgradeRepository;
import app.l2nx.highfive.repository.residence.FortFunctionRepository;
import app.l2nx.highfive.repository.residence.FortStateRepository;
import app.l2nx.highfive.repository.residence.model.FortDoorUpgrade;
import app.l2nx.highfive.repository.residence.model.FortState;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FortManager {
    private static volatile FortManager instance;

    private static final List<Fort> forts = new CopyOnWriteArrayList<>();

    private final FortStateRepository stateRepository;
    private final FortFunctionRepository functionRepository;
    private final FortDoorUpgradeRepository doorUpgradeRepository;

    public FortManager(
            FortStateRepository stateRepository,
            FortFunctionRepository functionRepository,
            FortDoorUpgradeRepository doorUpgradeRepository) {
        this.stateRepository = stateRepository;
        this.functionRepository = functionRepository;
        this.doorUpgradeRepository = doorUpgradeRepository;
        instance = this;
    }

    public int findNearestFortIndex(WorldObject obj) {
        return findNearestFortIndex(obj, Long.MAX_VALUE);
    }

    public int findNearestFortIndex(WorldObject obj, long maxDistanceValue) {
        int index = getFortIndex(obj);
        if (index < 0) {
            Fort fort;
            double distance;
            long maxDistance = maxDistanceValue;
            for (int i = 0; i < forts.size(); i++) {
                fort = forts.get(i);
                if (fort == null) {
                    continue;
                }

                distance = fort.getDistance(obj);
                if (maxDistance > distance) {
                    maxDistance = (long) distance;
                    index = i;
                }
            }
        }

        return index;
    }

    public Fort getFortById(int fortId) {
        for (Fort fort : forts) {
            if (fort.getResidenceId() == fortId) {
                return fort;
            }
        }

        return null;
    }

    public Fort getFortByOwner(Clan clan) {
        if (clan == null) {
            return null;
        }

        for (Fort fort : forts) {
            if (fort.getOwnerClan() == clan) {
                return fort;
            }
        }

        return null;
    }

    public Fort getFort(String name) {
        for (Fort fort : forts) {
            if (fort.getName().equalsIgnoreCase(name.trim())) {
                return fort;
            }
        }

        return null;
    }

    public Fort getFort(int x, int y, int z) {
        for (Fort fort : forts) {
            if (fort.checkIfInZone(x, y, z)) {
                return fort;
            }
        }

        return null;
    }

    public Fort getFort(WorldObject activeObject) {
        return getFort(activeObject.getX(), activeObject.getY(), activeObject.getZ());
    }

    public int getFortIndex(int fortId) {
        Fort fort;
        for (int i = 0; i < forts.size(); i++) {
            fort = forts.get(i);
            if ((fort != null) && (fort.getResidenceId() == fortId)) {
                return i;
            }
        }

        return -1;
    }

    public int getFortIndex(WorldObject activeObject) {
        return getFortIndex(activeObject.getX(), activeObject.getY(), activeObject.getZ());
    }

    public int getFortIndex(int x, int y, int z) {
        Fort fort;
        for (int i = 0; i < forts.size(); i++) {
            fort = forts.get(i);
            if ((fort != null) && fort.checkIfInZone(x, y, z)) {
                return i;
            }
        }

        return -1;
    }

    public List<Fort> getForts() {
        return forts;
    }

    public void loadInstances() {
        for (FortTemplate template : FortData.getInstance().getForts()) {
            forts.add(new Fort(template.id()));
        }

        log.info("Loaded {} fortress", forts.size());
    }

    /**
     * Loads the fort doors and starts the fort sieges; the siege settings of {@link FortSiegeManager} must be loaded.
     */
    public void activateInstances() {
        for (Fort fort : forts) {
            fort.activateInstance();
        }

        for (Fort fort : forts) {
            fort.getSiege().getSiegeGuardManager().loadSiegeGuard();
        }
    }

    public FortState findState(int fortId) {
        try {
            return stateRepository.findById(fortId).orElseGet(() -> initialState(fortId));
        } catch (DataAccessException e) {
            log.warn("Exception: loadFortData(): {}", e.getMessage(), e);
            return initialState(fortId);
        }
    }

    private static FortState initialState(int fortId) {
        return FortState.builder().fortId(fortId).build();
    }

    /**
     * Sets the owner and resets the castle contract.
     *
     * @param clanId {@code 0} leaves the fort without an owner
     * @param lastOwnedTime epoch milliseconds the owner took the fort, {@code 0} for none
     */
    public void saveOwner(int fortId, int clanId, long lastOwnedTime) {
        try {
            stateRepository.updateOwner(fortId, clanId, instantOrNull(lastOwnedTime));
        } catch (DataAccessException e) {
            log.warn("Exception: updateOwnerInDB(Pledge clan): {}", e.getMessage(), e);
        }
    }

    /**
     * @param castleId {@code 0} for none
     */
    public void saveContract(int fortId, int state, int castleId) {
        try {
            stateRepository.updateContract(fortId, state, castleId);
        } catch (DataAccessException e) {
            log.warn("Exception: setFortState(int state, int castleId): {}", e.getMessage(), e);
        }
    }

    /**
     * @param siegeDate epoch milliseconds
     */
    public void saveSiegeDate(int fortId, long siegeDate) {
        try {
            stateRepository.updateSiegeDate(fortId, instantOrNull(siegeDate));
        } catch (DataAccessException e) {
            log.warn("Exception: saveSiegeDate(): {}", e.getMessage(), e);
        }
    }

    public void saveSupplyLevel(int fortId, int supplyLevel) {
        try {
            stateRepository.updateSupplyLevel(fortId, supplyLevel);
        } catch (DataAccessException e) {
            log.warn("Exception: saveFortVariables(): {}", e.getMessage(), e);
        }
    }

    public List<ResidenceFunction> findFunctions(int fortId) {
        try {
            return functionRepository.findByFort(fortId);
        } catch (DataAccessException e) {
            log.error("Exception: Fort.loadFunctions(): {}", e.getMessage(), e);
            return List.of();
        }
    }

    public void saveFunction(int fortId, ResidenceFunction function) {
        try {
            functionRepository.save(fortId, function);
        } catch (DataAccessException e) {
            log.error("Could not save function {} of fort {}: {}", function.type(), fortId, e.getMessage(), e);
        }
    }

    public void deleteFunction(int fortId, int functionType) {
        try {
            functionRepository.delete(fortId, functionType);
        } catch (DataAccessException e) {
            log.error("Exception: Fort.removeFunctions(int functionType): {}", e.getMessage(), e);
        }
    }

    public List<FortDoorUpgrade> findDoorUpgrades(int fortId) {
        try {
            return doorUpgradeRepository.findByFort(fortId);
        } catch (DataAccessException e) {
            log.warn("Exception: loadFortDoorUpgrade(): {}", e.getMessage(), e);
            return List.of();
        }
    }

    public void saveDoorUpgrade(int fortId, FortDoorUpgrade upgrade) {
        try {
            doorUpgradeRepository.save(fortId, upgrade);
        } catch (DataAccessException e) {
            log.warn("Could not save door upgrade {} of fort {}: {}", upgrade.doorId(), fortId, e.getMessage(), e);
        }
    }

    public void deleteDoorUpgrades(int fortId) {
        try {
            doorUpgradeRepository.deleteByFort(fortId);
        } catch (DataAccessException e) {
            log.warn("Exception: removeDoorUpgrade(): {}", e.getMessage(), e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static FortManager getInstance() {
        final FortManager current = instance;
        if (current == null) {
            throw new IllegalStateException("FortManager is used before the Spring context created it.");
        }
        return current;
    }
}
