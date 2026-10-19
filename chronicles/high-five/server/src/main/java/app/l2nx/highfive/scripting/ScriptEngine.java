/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
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
package app.l2nx.highfive.scripting;

import app.l2nx.highfive.config.DevelopmentConfig;
import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.scripting.annotations.Disabled;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Document;

/**
 * Runs compiled scripts named relative to {@link #SCRIPTS_PACKAGE} by calling their {@code main}. Re-running reuses the loaded classes, so
 * changed script code needs a rebuild.
 * @author Mobius
 */
public class ScriptEngine implements IXmlReader {
    private static final Logger LOGGER = Logger.getLogger(ScriptEngine.class.getName());

    public static final String SCRIPTS_PACKAGE = "app.l2nx.highfive.scripts";
    public static final String MASTER_HANDLER = "handlers.MasterHandler";
    public static final String EFFECT_MASTER_HANDLER = "handlers.EffectMasterHandler";

    private final Map<String, Set<String>> exclusions = new HashMap<>();
    private final ScriptCatalog catalog = new ScriptCatalog(SCRIPTS_PACKAGE, exclusions);
    private String currentScript;

    protected ScriptEngine() {
        // Load Scripts.xml
        load();
    }

    @Override
    public void load() {
        exclusions.clear();
        parseDatapackFile("config/Scripts.xml");
        LOGGER.info("Loaded " + exclusions.size() + " script exclusions.");
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "exclude", excludeNode -> {
                    final Set<String> includes = exclusions.computeIfAbsent(
                            parseString(excludeNode.getAttributes(), "file"), _ -> new HashSet<>());
                    forEach(
                            excludeNode,
                            "include",
                            includeNode -> includes.add(parseString(includeNode.getAttributes(), "file")));
                }));
    }

    /**
     * @param script a script name ({@code quests.QuestMasterHandler}) or a Mobius script path ({@code quests/QuestMasterHandler.java})
     */
    public void executeScript(String script) throws Exception {
        final String name = ScriptCatalog.toScriptName(script);
        final Class<?> scriptClass;
        try {
            scriptClass = Class.forName(SCRIPTS_PACKAGE + "." + name);
        } catch (ClassNotFoundException e) {
            throw new Exception("Script does not exist: " + name, e);
        }

        if (scriptClass.isAnnotationPresent(Disabled.class)) {
            return;
        }

        // Same rule as the startup scan (ScriptCatalog): the class itself declares public static main(String[]).
        final Method main;
        try {
            main = scriptClass.getDeclaredMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw new Exception("Script " + name + " has no main method.", e);
        }

        if (!Modifier.isPublic(main.getModifiers()) || !Modifier.isStatic(main.getModifiers())) {
            throw new Exception("Script " + name + " has no public static main method.");
        }

        final String previous = currentScript;
        currentScript = name;
        try {
            main.invoke(null, (Object) new String[0]);
        } finally {
            currentScript = previous;
        }
    }

    public void executeScriptList() {
        if (DevelopmentConfig.NO_QUESTS) {
            return;
        }

        for (String script : catalog.scripts()) {
            try {
                executeScript(script);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, getClass().getSimpleName() + ": " + script + " failed execution!", e);
            }
        }
    }

    /**
     * @return the name of the script whose {@code main} is running, {@code null} outside script loading
     */
    public String getCurrentLoadingScript() {
        return currentScript;
    }

    public String getScriptFolder(Class<?> scriptClass) {
        return catalog.folderOf(scriptClass.getName());
    }

    public static ScriptEngine getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final ScriptEngine INSTANCE = new ScriptEngine();
    }
}
