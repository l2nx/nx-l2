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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GraciaSeedsConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Calendar;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeedOfDestructionManager {

    public static final String ENERGY_SEEDS = "EnergySeeds";

    private static final byte SOITYPE = 2;
    private static final byte SOATYPE = 3;

    // Seed of Destruction
    private static final byte SODTYPE = 1;

    @Getter
    private int soDTiatKilled = 0;

    @Getter
    private int soDState = 1;

    @Getter
    private final Calendar soDLastStateChangeDate;

    protected SeedOfDestructionManager() {
        soDLastStateChangeDate = ClockBridge.calendar();
        loadData();
        handleSodStages();
        log.info("Seed of Destruction Manager: Loaded. Current stage is: {}", soDState);
    }

    public void saveData(byte seedType) {
        switch (seedType) {
            case SODTYPE: {
                // Seed of Destruction
                GlobalVariablesManager.getInstance().set("SoDState", soDState);
                GlobalVariablesManager.getInstance().set("SoDTiatKilled", soDTiatKilled);
                GlobalVariablesManager.getInstance().set("SoDLSCDate", soDLastStateChangeDate.getTimeInMillis());
                break;
            }
            case SOITYPE: {
                // Seed of Infinity
                break;
            }
            case SOATYPE: {
                // Seed of Annihilation
                break;
            }
            default: {
                log.warn("Unknown SeedType in SaveData: {}", seedType);
                break;
            }
        }
    }

    public void loadData() {
        // Seed of Destruction variables
        if (GlobalVariablesManager.getInstance().hasVariable("SoDState")) {
            soDState = GlobalVariablesManager.getInstance().getInt("SoDState", 0);
            soDTiatKilled = GlobalVariablesManager.getInstance().getInt("SoDTiatKilled", 0);
            soDLastStateChangeDate.setTimeInMillis(
                    GlobalVariablesManager.getInstance().getLong("SoDLSCDate", 0));
        } else {
            // save Initial values
            saveData(SODTYPE);
        }
    }

    private void handleSodStages() {
        switch (soDState) {
            case 1: {
                // Do nothing players should kill Tiat a few times
                break;
            }
            case 2: {
                // Conquest Complete state, if too much time is passed than change to defense state
                final long timePast = ClockBridge.wallMillis() - soDLastStateChangeDate.getTimeInMillis();
                if (timePast >= GraciaSeedsConfig.SOD_STAGE_2_LENGTH) {
                    // change to Attack state because Defend statet is not implemented
                    setSoDState(1, true);
                } else {
                    ThreadPool.schedule(
                            () -> {
                                setSoDState(1, true);
                                updateSodState();
                            },
                            GraciaSeedsConfig.SOD_STAGE_2_LENGTH - timePast);
                }
                break;
            }
            case 3: {
                // not implemented
                setSoDState(1, true);
                break;
            }
            default: {
                log.warn("Unknown Seed of Destruction state({})! ", soDState);
            }
        }

        manageEdricSpawn();
    }

    private void manageEdricSpawn() {
        final Npc edric = World.getNpc(32527);
        if (soDState == 2) {
            if (edric == null) {
                Quest.addSpawn(32527, -248525, 250048, 4307, 24576);
            }
        } else if (edric != null) {
            edric.deleteMe();
        }
    }

    public void updateSodState() {
        final Quest quest = ScriptManager.getInstance().getScript(ENERGY_SEEDS);
        if (quest == null) {
            log.warn("Missing EnergySeeds Quest!");
        } else {
            quest.notifyEvent("StopSoDAi", null, null);
        }
    }

    public void increaseSoDTiatKilled() {
        if (soDState == 1) {
            soDTiatKilled++;
            if (soDTiatKilled >= GraciaSeedsConfig.SOD_TIAT_KILL_COUNT) {
                setSoDState(2, false);
            }

            saveData(SODTYPE);
            final Quest esQuest = ScriptManager.getInstance().getScript(ENERGY_SEEDS);
            if (esQuest == null) {
                log.warn("Missing EnergySeeds Quest!");
            } else {
                esQuest.notifyEvent("StartSoDAi", null, null);
            }
        }
    }

    public void setSoDState(int value, boolean doSave) {
        log.info("New Seed of Destruction state -> {}.", value);
        soDLastStateChangeDate.setTimeInMillis(ClockBridge.wallMillis());
        soDState = value;

        // Reset number of Tiat kills.
        if (soDState == 1) {
            soDTiatKilled = 0;
        }

        handleSodStages();

        if (doSave) {
            saveData(SODTYPE);
        }
    }

    public long getSoDTimeForNextStateChange() {
        switch (soDState) {
            case 1: {
                return -1;
            }
            case 2: {
                return ((soDLastStateChangeDate.getTimeInMillis() + GraciaSeedsConfig.SOD_STAGE_2_LENGTH)
                        - ClockBridge.wallMillis());
            }
            case 3: {
                // not implemented yet
                return -1;
            }
            default: {
                // this should not happen!
                return -1;
            }
        }
    }

    /**
     * Gets the single instance of {@code GraciaSeedsManager}.
     * @return single instance of {@code GraciaSeedsManager}
     */
    public static SeedOfDestructionManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final SeedOfDestructionManager INSTANCE = new SeedOfDestructionManager();
    }
}
