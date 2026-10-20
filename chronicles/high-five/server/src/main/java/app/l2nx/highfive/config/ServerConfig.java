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
package app.l2nx.highfive.config;

import app.l2nx.highfive.data.xml.IXmlReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * This class loads all the server related configurations.
 * @author Mobius
 */
@Slf4j
public class ServerConfig {

    private static final String SERVER_CONFIG_FILE = "./config/Server.ini";
    private static final String IPCONFIG_FILE = "./config/ipconfig.xml";
    private static final String CHAT_FILTER_FILE = "./config/chatfilter.txt";
    private static final String HEXID_FILE = "./config/hexid.txt";

    public static String GAMESERVER_HOSTNAME;
    public static int PORT_GAME;
    public static boolean PACKET_ENCRYPTION;
    public static int REQUEST_ID;
    public static boolean ACCEPT_ALTERNATE_ID;
    public static File DATAPACK_ROOT;
    public static File SCRIPT_ROOT;
    public static Pattern CHARNAME_TEMPLATE_PATTERN;
    public static Pattern PET_NAME_TEMPLATE_PATTERN;
    public static Pattern CLAN_NAME_TEMPLATE_PATTERN;
    public static int MAX_CHARACTERS_NUMBER_PER_ACCOUNT;
    public static int MAXIMUM_ONLINE_USERS;
    public static boolean HARDWARE_INFO_ENABLED;
    public static boolean KICK_MISSING_HWID;
    public static int MAX_PLAYERS_PER_HWID;
    public static boolean REQUIRE_FRESH_HWID;
    public static int HWID_CHECK_DELAY;
    public static List<Integer> PROTOCOL_LIST;
    public static int SERVER_LIST_TYPE;
    public static int SERVER_LIST_AGE;
    public static boolean SERVER_LIST_BRACKET;
    public static boolean DEADLOCK_WATCHER;
    public static int DEADLOCK_CHECK_INTERVAL;
    public static boolean RESTART_ON_DEADLOCK;
    public static boolean SERVER_RESTART_SCHEDULE_ENABLED;
    public static boolean SERVER_RESTART_SCHEDULE_MESSAGE;
    public static int SERVER_RESTART_SCHEDULE_COUNTDOWN;
    public static String[] SERVER_RESTART_SCHEDULE;
    public static List<Integer> SERVER_RESTART_DAYS;
    public static boolean PRECAUTIONARY_RESTART_ENABLED;
    public static boolean PRECAUTIONARY_RESTART_CPU;
    public static boolean PRECAUTIONARY_RESTART_MEMORY;
    public static boolean PRECAUTIONARY_RESTART_CHECKS;
    public static int PRECAUTIONARY_RESTART_PERCENTAGE;
    public static int PRECAUTIONARY_RESTART_DELAY;
    public static List<String> GAME_SERVER_SUBNETS;
    public static List<String> GAME_SERVER_HOSTS;

    public static List<String> FILTER_LIST;
    public static int SERVER_ID;
    public static byte[] HEX_ID;

    public static void load() {
        final ConfigReader config = new ConfigReader(SERVER_CONFIG_FILE);
        GAMESERVER_HOSTNAME = config.getString("GameserverHostname", "0.0.0.0");
        PORT_GAME = config.getInt("GameserverPort", 7777);
        PACKET_ENCRYPTION = config.getBoolean("PacketEncryption", false);
        REQUEST_ID = config.getInt("RequestServerID", 0);
        ACCEPT_ALTERNATE_ID = config.getBoolean("AcceptAlternateID", true);

        try {
            DATAPACK_ROOT = new File(config.getString("DatapackRoot", ".").replace('\\', '/')).getCanonicalFile();
        } catch (IOException e) {
            log.warn("Error setting datapack root!", e);
            DATAPACK_ROOT = new File(".");
        }

        try {
            SCRIPT_ROOT =
                    new File(config.getString("ScriptRoot", "./data/scripts").replace('\\', '/')).getCanonicalFile();
        } catch (Exception e) {
            log.warn("Error setting script root!", e);
            SCRIPT_ROOT = new File(".");
        }

        Pattern charNamePattern;
        try {
            charNamePattern = Pattern.compile(config.getString("CnameTemplate", ".*"));
        } catch (PatternSyntaxException e) {
            log.warn("Character name pattern is invalid!", e);
            charNamePattern = Pattern.compile(".*");
        }
        CHARNAME_TEMPLATE_PATTERN = charNamePattern;

        Pattern petNamePattern;
        try {
            petNamePattern = Pattern.compile(config.getString("PetNameTemplate", ".*"));
        } catch (PatternSyntaxException e) {
            log.warn("Pet name pattern is invalid!", e);
            petNamePattern = Pattern.compile(".*");
        }
        PET_NAME_TEMPLATE_PATTERN = petNamePattern;

        Pattern clanNamePattern;
        try {
            clanNamePattern = Pattern.compile(config.getString("ClanNameTemplate", ".*"));
        } catch (PatternSyntaxException e) {
            log.warn("Clan name pattern is invalid!", e);
            clanNamePattern = Pattern.compile(".*");
        }

        CLAN_NAME_TEMPLATE_PATTERN = clanNamePattern;
        MAX_CHARACTERS_NUMBER_PER_ACCOUNT = config.getInt("CharMaxNumber", 7);
        MAXIMUM_ONLINE_USERS = config.getInt("MaximumOnlineUsers", 100);
        HARDWARE_INFO_ENABLED = config.getBoolean("EnableHardwareInfo", false);
        KICK_MISSING_HWID = config.getBoolean("KickMissingHWID", false);
        MAX_PLAYERS_PER_HWID = config.getInt("MaxPlayersPerHWID", 0);
        if (MAX_PLAYERS_PER_HWID > 0) {
            KICK_MISSING_HWID = true;
        }
        REQUIRE_FRESH_HWID = config.getBoolean("RequireFreshHWID", false);
        HWID_CHECK_DELAY = config.getInt("HwidCheckDelay", 5000);

        final String[] protocols =
                config.getString("AllowedProtocolRevisions", "267;268;271;273").split(";");
        PROTOCOL_LIST = new ArrayList<>(protocols.length);
        for (String protocol : protocols) {
            try {
                PROTOCOL_LIST.add(Integer.parseInt(protocol.trim()));
            } catch (NumberFormatException e) {
                log.warn("Wrong config protocol version: {}. Skipped.", protocol);
            }
        }

        SERVER_LIST_TYPE =
                getServerTypeId(config.getString("ServerListType", "Free").split(","));
        SERVER_LIST_AGE = config.getInt("ServerListAge", 0);
        SERVER_LIST_BRACKET = config.getBoolean("ServerListBrackets", false);
        DEADLOCK_WATCHER = config.getBoolean("DeadlockWatcher", true);
        DEADLOCK_CHECK_INTERVAL = config.getInt("DeadlockCheckInterval", 20);
        RESTART_ON_DEADLOCK = config.getBoolean("RestartOnDeadlock", false);
        SERVER_RESTART_SCHEDULE_ENABLED = config.getBoolean("ServerRestartScheduleEnabled", false);
        SERVER_RESTART_SCHEDULE_MESSAGE = config.getBoolean("ServerRestartScheduleMessage", false);
        SERVER_RESTART_SCHEDULE_COUNTDOWN = config.getInt("ServerRestartScheduleCountdown", 600);
        SERVER_RESTART_SCHEDULE =
                config.getString("ServerRestartSchedule", "08:00").split(",");

        SERVER_RESTART_DAYS = new ArrayList<>();
        for (String day : config.getString("ServerRestartDays", "").trim().split(",")) {
            if (StringUtils.isNumeric(day)) {
                SERVER_RESTART_DAYS.add(Integer.parseInt(day));
            }
        }

        PRECAUTIONARY_RESTART_ENABLED = config.getBoolean("PrecautionaryRestartEnabled", false);
        PRECAUTIONARY_RESTART_CPU = config.getBoolean("PrecautionaryRestartCpu", true);
        PRECAUTIONARY_RESTART_MEMORY = config.getBoolean("PrecautionaryRestartMemory", false);
        PRECAUTIONARY_RESTART_CHECKS = config.getBoolean("PrecautionaryRestartChecks", true);
        PRECAUTIONARY_RESTART_PERCENTAGE = config.getInt("PrecautionaryRestartPercentage", 95);
        PRECAUTIONARY_RESTART_DELAY = config.getInt("PrecautionaryRestartDelay", 60) * 1000;

        final IPConfigData ipConfigData = new IPConfigData();
        GAME_SERVER_SUBNETS = ipConfigData.getSubnets();
        GAME_SERVER_HOSTS = ipConfigData.getHosts();

        loadChatFilter();

        loadHexid();
    }

    private static void loadChatFilter() {
        try {
            FILTER_LIST = Files.lines(Paths.get(CHAT_FILTER_FILE), StandardCharsets.UTF_8)
                    .map(String::trim)
                    .filter(line -> (!line.isEmpty() && (line.charAt(0) != '#')))
                    .toList();
            log.info("Loaded {} Filter Words.", FILTER_LIST.size());
        } catch (IOException e) {
            log.warn("Error while loading chat filter words!", e);
        }
    }

    private static void loadHexid() {
        loadHexid(HEXID_FILE);
    }

    /**
     * Loads the server id and the hex id the login server registered this game server under. Without a valid file both
     * stay unset: the game server asks for {@code RequestServerID} with a newly generated hex id, as on its first start.
     */
    static void loadHexid(String fileName) {
        SERVER_ID = 0;
        HEX_ID = null;
        if (!new File(fileName).exists()) {
            log.info("No HexID file {}: the game server registers on login with a new hex id.", fileName);
            return;
        }

        final ConfigReader stored = new ConfigReader(fileName);
        final String serverId = stored.getValue("ServerID");
        final String hexId = stored.getValue("HexID");
        if ((serverId == null) || (hexId == null)) {
            log.info(
                    "The HexID file {} lacks ServerID or HexID: the game server registers on login with a new hex id.",
                    fileName);
            return;
        }

        try {
            final int id = Integer.parseInt(serverId.trim());
            final byte[] hex = HexFormat.of().parseHex(hexId.trim());
            if (hex.length == 0) {
                throw new IllegalArgumentException("the hex id is empty");
            }
            SERVER_ID = id;
            HEX_ID = hex;
        } catch (IllegalArgumentException e) {
            // A hexid.txt written by L2J Mobius holds a signed BigInteger: odd length or a minus sign fail here.
            log.info(
                    "The HexID file {} is not a server id with an even-length hex id ({}): the game server registers"
                            + " on login with a new hex id.",
                    fileName,
                    e.getMessage());
        }
    }

    /** Skips the write when the file already holds these values. */
    public static void saveHexid(int serverId, String hexId) {
        saveHexid(serverId, hexId, HEXID_FILE);
    }

    static void saveHexid(int serverId, String hexId, String fileName) {
        // The client key packet and the captcha crests read the id now, not after the next start.
        SERVER_ID = serverId;
        HEX_ID = HexFormat.of().parseHex(hexId);
        final File file = new File(fileName);
        if (file.exists()) {
            final ConfigReader stored = new ConfigReader(fileName);
            if (String.valueOf(serverId).equals(stored.getValue("ServerID"))
                    && hexId.equals(stored.getValue("HexID"))) {
                return;
            }
        }

        final Properties hexSetting = new Properties();
        hexSetting.setProperty("ServerID", String.valueOf(serverId));
        hexSetting.setProperty("HexID", hexId);
        try (OutputStream out = new FileOutputStream(file)) {
            hexSetting.store(out, "The HexId to Auth into LoginServer");
            log.info("Saved the HexID file {} for server id {}.", fileName, serverId);
        } catch (IOException e) {
            log.warn("Failed to save the HexID file {}.", fileName, e);
        }
    }

    public static int getServerTypeId(String[] serverTypes) {
        int serverType = 0;
        for (String cType : serverTypes) {
            switch (cType.trim().toLowerCase()) {
                case "normal": {
                    serverType |= 0x01;
                    break;
                }
                case "relax": {
                    serverType |= 0x02;
                    break;
                }
                case "test": {
                    serverType |= 0x04;
                    break;
                }
                case "nolabel": {
                    serverType |= 0x08;
                    break;
                }
                case "restricted": {
                    serverType |= 0x10;
                    break;
                }
                case "event": {
                    serverType |= 0x20;
                    break;
                }
                case "free": {
                    serverType |= 0x40;
                    break;
                }
                default: {
                    break;
                }
            }
        }

        return serverType;
    }

    @Slf4j
    private static class IPConfigData implements IXmlReader {
        private static final List<String> subnets = new ArrayList<>(5);
        private static final List<String> hosts = new ArrayList<>(5);

        public IPConfigData() {
            load();
        }

        @Override
        public void load() {
            final File file = new File(IPCONFIG_FILE);
            if (file.exists()) {
                log.info("Network Config: ipconfig.xml exists, using manual configuration...");
                parseFile(new File(IPCONFIG_FILE));
            } else {
                log.info("Network Config: ipconfig.xml does not exist, using automatic configuration...");
                autoIpConfig();
            }
        }

        @Override
        public void parseDocument(Document document, File file) {
            NamedNodeMap attrs;
            for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
                if ("gameserver".equalsIgnoreCase(n.getNodeName())) {
                    for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                        if ("define".equalsIgnoreCase(d.getNodeName())) {
                            attrs = d.getAttributes();
                            subnets.add(attrs.getNamedItem("subnet").getNodeValue());
                            hosts.add(attrs.getNamedItem("address").getNodeValue());

                            if (hosts.size() != subnets.size()) {
                                log.warn(
                                        "Failed to Load {} File - subnets does not match server addresses.",
                                        IPCONFIG_FILE);
                            }
                        }
                    }

                    final Node att = n.getAttributes().getNamedItem("address");
                    if (att == null) {
                        log.warn("Failed to load {} file - default server address is missing.", IPCONFIG_FILE);
                        hosts.add("127.0.0.1");
                    } else {
                        hosts.add(att.getNodeValue());
                    }

                    subnets.add("0.0.0.0/0");
                }
            }
        }

        protected void autoIpConfig() {
            String externalIp = "127.0.0.1";
            try {
                final URL autoIp = URI.create("http://checkip.amazonaws.com").toURL();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(autoIp.openStream()))) {
                    externalIp = in.readLine();
                }
            } catch (IOException e) {
                log.info("Failed to connect to checkip.amazonaws.com please check your internet connection using"
                        + " 127.0.0.1!");
                externalIp = "127.0.0.1";
            }

            try {
                final Enumeration<NetworkInterface> niList = NetworkInterface.getNetworkInterfaces();
                while (niList.hasMoreElements()) {
                    final NetworkInterface ni = niList.nextElement();
                    if (!ni.isUp() || ni.isVirtual()) {
                        continue;
                    }

                    if (!ni.isLoopback()
                            && ((ni.getHardwareAddress() == null) || (ni.getHardwareAddress().length != 6))) {
                        continue;
                    }

                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getAddress() instanceof Inet6Address) {
                            continue;
                        }

                        final String hostAddress = ia.getAddress().getHostAddress();
                        final int subnetPrefixLength = ia.getNetworkPrefixLength();
                        final int subnetMaskInt = IntStream.rangeClosed(1, subnetPrefixLength)
                                        .reduce((r, _) -> (r << 1) + 1)
                                        .orElse(0)
                                << (32 - subnetPrefixLength);
                        final int hostAddressInt = Arrays.stream(hostAddress.split("\\."))
                                .mapToInt(Integer::parseInt)
                                .reduce((r, e) -> (r << 8) + e)
                                .orElse(0);
                        final int subnetAddressInt = hostAddressInt & subnetMaskInt;
                        final String subnetAddress =
                                ((subnetAddressInt >> 24) & 0xFF) + "." + ((subnetAddressInt >> 16) & 0xFF) + "."
                                        + ((subnetAddressInt >> 8) & 0xFF) + "." + (subnetAddressInt & 0xFF);
                        final String subnet = subnetAddress + '/' + subnetPrefixLength;
                        if (!subnets.contains(subnet) && !subnet.equals("0.0.0.0/0")) {
                            subnets.add(subnet);
                            hosts.add(hostAddress);
                            log.info("Network Config: Adding new subnet: {} address: {}", subnet, hostAddress);
                        }
                    }
                }

                hosts.add(externalIp);
                subnets.add("0.0.0.0/0");
                log.info("Network Config: Adding new subnet: 0.0.0.0/0 address: {}", externalIp);
            } catch (SocketException e) {
                throw new IllegalStateException(
                        "Network Config: Configuration failed please configure manually using ipconfig.xml", e);
            }
        }

        protected List<String> getSubnets() {
            if (subnets.isEmpty()) {
                return Arrays.asList("0.0.0.0/0");
            }

            return subnets;
        }

        protected List<String> getHosts() {
            if (hosts.isEmpty()) {
                return Arrays.asList("127.0.0.1");
            }

            return hosts;
        }
    }
}
