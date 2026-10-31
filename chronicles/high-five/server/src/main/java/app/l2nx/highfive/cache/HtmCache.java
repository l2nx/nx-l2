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
package app.l2nx.highfive.cache;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Layane, Mobius
 */
@Slf4j
public class HtmCache {

    private static final Map<String, String> HTML_CACHE =
            GeneralConfig.HTM_CACHE ? new HashMap<>() : new ConcurrentHashMap<>();

    @Getter
    private int loadedFiles;

    private long bytesBuffLen;

    protected HtmCache() {
        reload();
    }

    public void reload() {
        reload(ServerConfig.DATAPACK_ROOT);
    }

    public void reload(File file) {
        if (GeneralConfig.HTM_CACHE) {
            log.info("Html cache start...");
            parseDir(file);
            log.info(
                    "Cache[HTML]: {} megabytes on {} files loaded.",
                    String.format("%.3f", getMemoryUsage()),
                    loadedFiles);
        } else {
            HTML_CACHE.clear();
            loadedFiles = 0;
            bytesBuffLen = 0;
            log.info("Cache[HTML]: Running lazy cache.");
        }
    }

    public void reloadPath(File file) {
        parseDir(file);
        log.info("Cache[HTML]: Reloaded specified path.");
    }

    public double getMemoryUsage() {
        return (float) bytesBuffLen / 1048576;
    }

    private void parseDir(File dir) {
        final File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) {
                    loadFile(file);
                } else {
                    parseDir(file);
                }
            }
        }
    }

    public String loadFile(File file) {
        if ((file == null) || !file.isFile()) {
            return null;
        }

        final String lowerCaseName = file.getName().toLowerCase();
        if (!(lowerCaseName.endsWith(".htm") || lowerCaseName.endsWith(".html"))) {
            return null;
        }

        String filePath = null;
        String content = null;
        try (FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis)) {
            final int bytes = bis.available();
            final byte[] raw = new byte[bytes];

            bis.read(raw);
            content = new String(raw, StandardCharsets.UTF_8);
            content = content.replaceAll("(?s)<!--.*?-->", ""); // Remove html comments.
            content = content.replaceAll("[\\t\\n]", ""); // Remove tabs and new lines.

            filePath = file.toURI()
                    .getPath()
                    .substring(ServerConfig.DATAPACK_ROOT.toURI().getPath().length());
            if (GeneralConfig.CHECK_HTML_ENCODING
                    && !filePath.startsWith("data/lang")
                    && !StandardCharsets.US_ASCII.newEncoder().canEncode(content)) {
                log.warn("HTML encoding check: File {} contains non ASCII content.", filePath);
            }

            final String oldContent = HTML_CACHE.put(filePath, content);
            if (oldContent == null) {
                bytesBuffLen += bytes;
                loadedFiles++;
            } else {
                bytesBuffLen = (bytesBuffLen - oldContent.length()) + bytes;
            }
        } catch (Exception e) {
            log.warn("Problem with htm file:", e);
        }

        return content;
    }

    public String getHtm(Player player, String path) {
        // Some page names come from client bypasses, a parent segment would leave the html folders.
        if (path.contains("..")) {
            log.warn("Refused path {} requested by {}.", path, player);
            return null;
        }

        final String prefix = player != null ? player.getHtmlPrefix() : "";
        String newPath = prefix + path;
        String content = HTML_CACHE.get(newPath);
        if (!GeneralConfig.HTM_CACHE && (content == null)) {
            content = loadFile(new File(ServerConfig.DATAPACK_ROOT, newPath));
            if (content == null) {
                content = loadFile(new File(ServerConfig.SCRIPT_ROOT, newPath));
            }
        }

        // In case localisation does not exist try the default path.
        if ((content == null) && !prefix.contentEquals("")) {
            content = HTML_CACHE.get(path);
            newPath = path;
        }

        if ((player != null) && player.isGM() && GeneralConfig.GM_DEBUG_HTML_PATHS) {
            player.sendPacket(new CreatureSay(null, ChatType.GENERAL, "HTML", newPath.substring(5)));
        }

        return content;
    }

    public boolean contains(String path) {
        return HTML_CACHE.containsKey(path);
    }

    public static HtmCache getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final HtmCache INSTANCE = new HtmCache();
    }
}
