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

import app.l2nx.highfive.config.DevelopmentConfig;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.scripting.ScriptEngine;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScriptManager {

    /** Map containing all the quests. */
    @Getter
    private final Map<String, Quest> quests = new ConcurrentHashMap<>();
    /** Map containing all the scripts. */
    @Getter
    private final Map<String, Quest> scripts = new ConcurrentHashMap<>();

    protected ScriptManager() {}

    public void reload(String questFolder) {
        final Quest q = getScript(questFolder);
        if (q != null) {
            q.reload();
        }
    }

    /**
     * Reloads a the quest by ID.
     * @param questId the ID of the quest to be reloaded
     */
    public void reload(int questId) {
        final Quest q = getQuest(questId);
        if (q != null) {
            q.reload();
        }
    }

    /**
     * Unload all quests and scripts and reload them.
     */
    public void reloadAllScripts() {
        unloadAllScripts();

        log.info("Reloading all server scripts.");

        try {
            ScriptEngine.getInstance().executeScriptList();
        } catch (Exception e) {
            log.error("Failed executing script list!", e);
        }

        getInstance().report();
    }

    /**
     * Unload all quests and scripts.
     */
    public void unloadAllScripts() {
        log.info("Unloading all server scripts.");

        // Unload quests.
        for (Quest quest : quests.values()) {
            if (quest != null) {
                quest.unload(false);
            }
        }

        quests.clear();

        // Unload scripts.
        for (Quest script : scripts.values()) {
            if (script != null) {
                script.unload(false);
            }
        }

        scripts.clear();
    }

    /**
     * Logs how many quests and scripts are loaded.
     */
    public void report() {
        log.info("Loaded {} quests.", quests.size());
        log.info("Loaded {} scripts.", scripts.size());
    }

    /**
     * Calls {@link Quest#onSave()} in all quests and scripts.
     */
    public void save() {
        // Save quests.
        for (Quest quest : quests.values()) {
            quest.onSave();
        }

        // Save scripts.
        for (Quest script : scripts.values()) {
            script.onSave();
        }
    }

    /**
     * Retrieves a Quest object by its name from either the quests or scripts map.<br>
     * This method first checks the quests map for the specified name.<br>
     * If not found, it falls back to checking the scripts map.
     * @param name the name of the quest/script to retrieve
     * @return the Quest object associated with the given name if found in either map, or null if the name is not present in either map
     */
    public Quest getScript(String name) {
        final Quest quest = quests.get(name);
        return (quest != null) ? quest : scripts.get(name);
    }

    /**
     * Gets a quest by ID.
     * @param questId the ID of the quest to get
     * @return if found, the quest, {@code null} otherwise
     */
    public Quest getQuest(int questId) {
        for (Quest q : quests.values()) {
            if (q.getId() == questId) {
                return q;
            }
        }

        return null;
    }

    /**
     * Adds a new quest.
     * @param quest the quest to be added
     */
    public void addQuest(Quest quest) {
        if (quest == null) {
            throw new IllegalArgumentException("Quest argument cannot be null");
        }

        // FIXME: unloading the old quest at this point is a tad too late.
        // the new quest has already initialized itself and read the data, starting
        // an unpredictable number of tasks with that data. The old quest will now
        // save data which will never be read.
        // However, requesting the newQuest to re-read the data is not necessarily a
        // good option, since the newQuest may have already started timers, spawned NPCs
        // or taken any other action which it might re-take by re-reading the data.
        // the current solution properly closes the running tasks of the old quest but
        // ignores the data; perhaps the least of all evils...
        final Quest old = quests.put(quest.getName(), quest);
        if (old != null) {
            old.unload();
            log.info("Replaced quest {} ({}) with a new version!", old.getName(), old.getId());
        }

        if (DevelopmentConfig.SHOW_QUEST_LOAD_IN_LOGS) {
            final String questName = quest.getName().contains("_")
                    ? quest.getName().substring(quest.getName().indexOf('_') + 1)
                    : quest.getName();
            log.info("Loaded quest {}.", separateWords(questName));
        }
    }

    /**
     * Removes a script.
     * @param script the script to remove
     * @return {@code true} if the script was removed, {@code false} otherwise
     */
    public boolean removeScript(Quest script) {
        if (quests.containsKey(script.getName())) {
            quests.remove(script.getName());
            return true;
        } else if (scripts.containsKey(script.getName())) {
            scripts.remove(script.getName());
            return true;
        }

        return false;
    }

    public boolean unload(Quest ms) {
        ms.onSave();
        return removeScript(ms);
    }

    /**
     * Adds a script.
     * @param script the script to be added
     */
    public void addScript(Quest script) {
        final Quest old = scripts.put(script.getClass().getSimpleName(), script);
        if (old != null) {
            old.unload();
            log.info("Replaced script {} with a new version!", old.getName());
        }

        if (DevelopmentConfig.SHOW_SCRIPT_LOAD_IN_LOGS) {
            log.info("Loaded script {}.", separateWords(script.getClass().getSimpleName()));
        }
    }

    private static String separateWords(String text) {
        final StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char current = text.charAt(i);
            if (Character.isUpperCase(current) && (i > 0) && Character.isLowerCase(text.charAt(i - 1))) {
                result.append(' ');
            }

            result.append(current);
        }

        return result.toString();
    }

    /**
     * Gets the single instance of {@code ScriptManager}.
     * @return single instance of {@code ScriptManager}
     */
    public static ScriptManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final ScriptManager INSTANCE = new ScriptManager();
    }
}
