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
package app.l2nx.highfive.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/**
 * ConfigReader is a utility class that reads and provides access to configuration properties from a file.
 * @author Mobius
 */
@Slf4j
public class ConfigReader {

    private final Properties properties = new Properties();
    private final File file;

    /**
     * Constructs a ConfigReader with the specified file path using the system's default Charset.
     * @param filePath the path to the configuration file
     */
    public ConfigReader(String filePath) {
        file = new File(filePath);

        if (!Files.exists(file.toPath())) {
            log.warn("Configuration file not found: {}", file.getAbsolutePath());
            return;
        }

        try (InputStream input = Files.newInputStream(file.toPath());
                InputStreamReader reader = new InputStreamReader(input, Charset.defaultCharset())) {
            properties.load(reader);
        } catch (IOException e) {
            log.warn("Failed to load configurations from {}: {}", file.getName(), e.getMessage());
        }
    }

    /**
     * Checks if the specified configuration key exists in the configurations.
     * @param config the configuration key
     * @return true if the key exists, false otherwise
     */
    public boolean containsKey(String config) {
        return properties.containsKey(config);
    }

    /**
     * Retrieves the value associated with the specified key as a String.
     * @param config the configuration key
     * @return the property value as a String, or null if the key does not exist
     */
    public String getValue(String config) {
        return properties.getProperty(config);
    }

    /**
     * Returns an unmodifiable collection of all property names that have string values.
     * @return a {@link Collection} of property names as strings.
     */
    public Collection<String> getStringPropertyNames() {
        return properties.stringPropertyNames();
    }

    /**
     * Retrieves the value associated with the specified key as a boolean.
     * @param config the configuration key
     * @param defaultValue the default value if the key does not exist or is malformed
     * @return the property value as a boolean, or the default value if the key does not exist or is malformed
     */
    public boolean getBoolean(String config, boolean defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                log.warn(
                        "Invalid boolean for config '{}' in file '{}', using default: {}.",
                        config,
                        file.getName(),
                        defaultValue);
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
        }

        return defaultValue;
    }

    /**
     * Retrieves the value associated with the specified key as a byte.
     * @param config the configuration key
     * @param defaultValue the default value if the key does not exist or is malformed
     * @return the property value as a byte, or the default value if the key does not exist or is malformed
     */
    public byte getByte(String config, byte defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Byte.parseByte(value);
            } catch (Exception e) {
                log.warn(
                        "Invalid byte for config '{}' in file '{}', using default: {}.",
                        config,
                        file.getName(),
                        defaultValue);
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
        }

        return defaultValue;
    }

    /**
     * Retrieves the value associated with the specified key as a short.
     * @param config the configuration key
     * @param defaultValue the default value if the key does not exist or is malformed
     * @return the property value as a short, or the default value if the key does not exist or is malformed
     */
    public short getShort(String config, short defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Short.parseShort(value);
            } catch (Exception e) {
                log.warn(
                        "Invalid short for config '{}' in file '{}', using default: {}.",
                        config,
                        file.getName(),
                        defaultValue);
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
        }

        return defaultValue;
    }

    /**
     * Retrieves the value associated with the specified key as an int.
     * @param config the configuration key
     * @param defaultValue the default value if the key does not exist or is malformed
     * @return the property value as an int, or the default value if the key does not exist or is malformed
     */
    public int getInt(String config, int defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                log.warn(
                        "Invalid int for config '{}' in file '{}', using default: {}.",
                        config,
                        file.getName(),
                        defaultValue);
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
        }

        return defaultValue;
    }

    /**
     * Retrieves the value associated with the specified key as a long.
     * @param config the configuration key
     * @param defaultValue the default value if the key does not exist or is malformed
     * @return the property value as a long, or the default value if the key does not exist or is malformed
     */
    public long getLong(String config, long defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                log.warn(
                        "Invalid long for config '{}' in file '{}', using default: {}.",
                        config,
                        file.getName(),
                        defaultValue);
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
        }

        return defaultValue;
    }

    /**
     * Retrieves the value associated with the specified key as a float.
     * @param config the configuration key
     * @param defaultValue the default value if the key does not exist or is malformed
     * @return the property value as a float, or the default value if the key does not exist or is malformed
     */
    public float getFloat(String config, float defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Float.parseFloat(value);
            } catch (Exception e) {
                log.warn(
                        "Invalid float for config '{}' in file '{}', using default: {}.",
                        config,
                        file.getName(),
                        defaultValue);
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
        }

        return defaultValue;
    }

    /**
     * Retrieves the value associated with the specified key as a double.
     * @param config the configuration key
     * @param defaultValue the default value if the key does not exist or is malformed
     * @return the property value as a double, or the default value if the key does not exist or is malformed
     */
    public double getDouble(String config, double defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (Exception e) {
                log.warn(
                        "Invalid double for config '{}' in file '{}', using default: {}.",
                        config,
                        file.getName(),
                        defaultValue);
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
        }

        return defaultValue;
    }

    /**
     * Retrieves the value associated with the specified key as a String.
     * @param config the configuration key
     * @param defaultValue the default value if the key does not exist
     * @return the property value as a String, or the default value if the key does not exist
     */
    public String getString(String config, String defaultValue) {
        final String value = properties.getProperty(config);
        if (value == null) {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
            return defaultValue;
        }

        return value;
    }

    /**
     * Retrieves the value associated with the specified key as an enum constant of the specified type.
     * @param <T> the type of the enum
     * @param config the configuration key
     * @param clazz the enum class to parse the value as
     * @param defaultValue the default value if the key does not exist or is malformed
     * @return the property value as an enum constant, or the default value if the key does not exist or is malformed
     */
    public <T extends Enum<T>> T getEnum(String config, Class<T> clazz, T defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Enum.valueOf(clazz, value);
            } catch (Exception e) {
                log.warn(
                        "Invalid enum for config '{}' in file '{}', using default: {}.",
                        config,
                        file.getName(),
                        defaultValue);
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default: {}.", config, file.getName(), defaultValue);
        }

        return defaultValue;
    }

    /**
     * Retrieves the value associated with the specified key as an int array.
     * @param config the configuration key
     * @param delimiter the separator used to split the string into integers
     * @param defaultValue the default value as a string if the key does not exist or is malformed
     * @return the property value, or the default value if the key does not exist or is malformed, as an int array
     */
    public int[] getIntArray(String config, String delimiter, String defaultValue) {
        final String value = properties.getProperty(config);
        if (value != null) {
            try {
                return Arrays.stream(value.split(delimiter))
                        .map(String::trim)
                        .mapToInt(Integer::parseInt)
                        .toArray();
            } catch (NumberFormatException e) {
                log.warn(
                        "Invalid int array for config '{}' in file '{}', using default values.",
                        config,
                        file.getName());
            }
        } else {
            log.warn("Config '{}' not found in file '{}', using default values.", config, file.getName());
        }

        try {
            return Arrays.stream(defaultValue.split(delimiter))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .toArray();
        } catch (NumberFormatException e) {
            log.warn("Invalid default values for config '{}' in file '{}', using empty array.", config, file.getName());
        }

        return new int[0];
    }
}
