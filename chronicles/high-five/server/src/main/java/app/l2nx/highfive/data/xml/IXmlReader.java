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
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.config.ServerConfig;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Interface for XML parsers.
 * @author Zoey76, Mobius
 */
public interface IXmlReader {

    static final String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    static final String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";

    String PARSE_POOL = "l2nx-datapack-parse";

    /**
     * Loads or reloads the data. It is recommended to clear the data storage (either a list or a map) before loading.
     */
    void load();

    /**
     * Parses an XML file located within the datapack directory. This is a helper method for {@link #parseFile(File)}.
     * @param path the relative path of the XML file within the datapack directory.
     */
    default void parseDatapackFile(String path) {
        parseFile(new File(".", path));
    }

    /**
     * Parses a single XML file. Calls {@link #parseDocument(Document, File)} if the file is successfully parsed. <b>Validation is enabled by default.</b>
     * @param file the XML file to parse.
     */
    default void parseFile(File file) {
        readDocument(file).ifPresent(document -> applyDocument(document, file));
    }

    private Optional<Document> readDocument(File file) {
        if (!isValidXmlFile(file)) {
            LoggerFactory.getLogger(getClass())
                    .warn("Cannot parse {}: file does not exist or is not valid.", file.getName());
            return Optional.empty();
        }

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(isValidating());
        factory.setIgnoringComments(true);
        try {
            factory.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(IXmlReader::resolveLocalEntity);
            return Optional.of(builder.parse(file));
        } catch (SAXParseException e) {
            LoggerFactory.getLogger(getClass())
                    .warn(
                            "Error parsing {} at line {}, column {}.",
                            file.getName(),
                            e.getLineNumber(),
                            e.getColumnNumber(),
                            e);
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Error parsing {}", file.getName(), e);
        }
        return Optional.empty();
    }

    // Without an own resolver every schema reference goes through the JDK built-in catalog, one instance per JVM that
    // is not thread-safe: two concurrent parses can fail one of them (ArrayIndexOutOfBoundsException in
    // CatalogImpl.markAsSearched), and the reader then loads nothing from that file.
    private static InputSource resolveLocalEntity(String publicId, String systemId) throws SAXException {
        if ((systemId == null) || !systemId.startsWith("file:")) {
            throw new SAXException("A datapack file may reference only local files, not " + systemId);
        }
        return new InputSource(systemId);
    }

    private void applyDocument(Document document, File file) {
        try {
            parseDocument(document, file);
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Error parsing {}", file.getName(), e);
        }
    }

    /**
     * Parses XML files in the specified directory. This is a helper method for {@link #parseDirectory(File, boolean)}.
     * @param directory the path to the directory with XML files.
     * @return {@code false} if the directory is not found, {@code true} otherwise.
     */
    default boolean parseDirectory(File directory) {
        return parseDirectory(directory, false);
    }

    /**
     * Parses XML files in a directory within the datapack. This is a helper method for {@link #parseDirectory(File, boolean)}.
     * @param path the path to the directory within the datapack.
     * @param recursive if {@code true}, parses files in all subdirectories.
     * @return {@code false} if the directory is not found, {@code true} otherwise.
     */
    default boolean parseDatapackDirectory(String path, boolean recursive) {
        return parseDirectory(new File(".", path), recursive);
    }

    /**
     * Loads all XML files from the specified directory and parses each file.
     * @param directory the directory to scan for XML files.
     * @param recursive if {@code true}, parses files in all subdirectories.
     * @return {@code false} if the directory is not found, {@code true} otherwise.
     */
    default boolean parseDirectory(File directory, boolean recursive) {
        return parseDirectory(directory, recursive, loadingThreads());
    }

    /**
     * Loads all XML files from the specified directory and parses each file.
     * @param directory the directory to scan for XML files.
     * @param recursive if {@code true}, parses files in all subdirectories.
     * @param threads the number of threads that parse the XML; {@code 1} parses on the calling thread.
     * @return {@code false} if the directory is not found, {@code true} otherwise.
     */
    default boolean parseDirectory(File directory, boolean recursive, int threads) {
        if (!directory.exists()) {
            LoggerFactory.getLogger(getClass()).warn("Directory not found: {}", directory.getAbsolutePath());
            return false;
        }

        final List<File> files = new ArrayList<>();
        collectXmlFiles(directory, recursive, files);
        // Only the XML parse runs on the pool: parseDocument touches world state, so it runs here, in file order.
        parseAll(
                files,
                threads,
                this::readDocument,
                (file, document) -> document.ifPresent(read -> applyDocument(read, file)));
        return true;
    }

    private static int loadingThreads() {
        return ServerConfig.THREADS_FOR_LOADING ? Runtime.getRuntime().availableProcessors() : 1;
    }

    private void collectXmlFiles(File directory, boolean recursive, List<File> files) {
        final File[] entries = directory.listFiles();
        if (entries == null) {
            return;
        }

        for (File entry : entries) {
            if (recursive && entry.isDirectory()) {
                collectXmlFiles(entry, true, files);
            } else if (isValidXmlFile(entry)) {
                files.add(entry);
            }
        }
    }

    static <S, R> void parseAll(
            List<S> sources, Function<? super S, ? extends R> parser, BiConsumer<? super S, ? super R> consumer) {
        parseAll(sources, loadingThreads(), parser, consumer);
    }

    static <S, R> void parseAll(
            List<S> sources,
            int threads,
            Function<? super S, ? extends R> parser,
            BiConsumer<? super S, ? super R> consumer) {
        if (threads > 1) {
            parseInParallel(sources, threads, parser, consumer);
        } else {
            for (S source : sources) {
                consumer.accept(source, parser.apply(source));
            }
        }
    }

    static <S, R> void parseInParallel(
            List<S> sources,
            int threads,
            Function<? super S, ? extends R> parser,
            BiConsumer<? super S, ? super R> consumer) {
        if (sources.isEmpty()) {
            return;
        }

        final int poolSize = Math.min(sources.size(), threads);
        final ExecutorService pool = Executors.newFixedThreadPool(
                poolSize, Thread.ofPlatform().name(PARSE_POOL + "-", 1).factory());
        final MeterRegistry registry = Metrics.globalRegistry;
        try (ExecutorService monitored = ExecutorServiceMetrics.monitor(registry, pool, PARSE_POOL)) {
            final Iterator<S> pending = sources.iterator();
            final ArrayDeque<Map.Entry<S, Future<R>>> ahead = new ArrayDeque<>();
            while (pending.hasNext() || !ahead.isEmpty()) {
                while (pending.hasNext() && (ahead.size() < (2 * poolSize))) {
                    final S source = pending.next();
                    ahead.add(Map.entry(source, monitored.<R>submit(() -> parser.apply(source))));
                }

                final Map.Entry<S, Future<R>> next = ahead.remove();
                consumer.accept(next.getKey(), next.getValue().get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while parsing the datapack", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            if (e.getCause() instanceof Error failure) {
                throw failure;
            }
            throw new IllegalStateException(e.getCause());
        } finally {
            removePoolMeters(registry);
        }
    }

    // A fresh pool per parse: gauges of a closed pool stay registered and block the next pool's registration.
    private static void removePoolMeters(MeterRegistry registry) {
        for (Meter meter : List.copyOf(registry.getMeters())) {
            if (PARSE_POOL.equals(meter.getId().getTag("name"))) {
                registry.remove(meter);
            }
        }
    }

    /**
     * Abstract method for parsing the current document. Called from {@link #parseFile(File)}.
     * @param document the document to parse
     * @param file the file being processed
     */
    void parseDocument(Document document, File file);

    /**
     * Parses a boolean value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed boolean value, or the default value if the node is null
     */
    default Boolean parseBoolean(Node node, Boolean defaultValue) {
        return node != null ? Boolean.valueOf(node.getNodeValue()) : defaultValue;
    }

    /**
     * Parses a boolean value from the given node.
     * @param node the XML node to parse
     * @return the parsed boolean value, or null if the node is null
     */
    default Boolean parseBoolean(Node node) {
        return parseBoolean(node, null);
    }

    /**
     * Parses a boolean value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @return the parsed boolean value, or null if the attribute is not found
     */
    default Boolean parseBoolean(NamedNodeMap attributes, String name) {
        return parseBoolean(attributes.getNamedItem(name));
    }

    /**
     * Parses a boolean value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if the attribute is not found
     * @return the parsed boolean value, or the default value if the attribute is not found
     */
    default Boolean parseBoolean(NamedNodeMap attributes, String name, Boolean defaultValue) {
        return parseBoolean(attributes.getNamedItem(name), defaultValue);
    }

    /**
     * Parses a byte value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed byte value, or the default value if the node is null
     */
    default Byte parseByte(Node node, Byte defaultValue) {
        return node != null ? Byte.decode(node.getNodeValue()) : defaultValue;
    }

    /**
     * Parses a byte value from the given node.
     * @param node the XML node to parse
     * @return the parsed byte value, or null if the node is null
     */
    default Byte parseByte(Node node) {
        return parseByte(node, null);
    }

    /**
     * Parses a byte value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @return the parsed byte value, or null if the attribute is not found
     */
    default Byte parseByte(NamedNodeMap attributes, String name) {
        return parseByte(attributes.getNamedItem(name));
    }

    /**
     * Parses a byte value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if the attribute is not found
     * @return the parsed byte value, or the default value if the attribute is not found
     */
    default Byte parseByte(NamedNodeMap attributes, String name, Byte defaultValue) {
        return parseByte(attributes.getNamedItem(name), defaultValue);
    }

    /**
     * Parses a short value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed short value, or the default value if the node is null
     */
    default Short parseShort(Node node, Short defaultValue) {
        return node != null ? Short.decode(node.getNodeValue()) : defaultValue;
    }

    /**
     * Parses a short value from the given node.
     * @param node the XML node to parse
     * @return the parsed short value, or null if the node is null
     */
    default Short parseShort(Node node) {
        return parseShort(node, null);
    }

    /**
     * Parses a short value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @return the parsed short value, or null if the attribute is not found
     */
    default Short parseShort(NamedNodeMap attributes, String name) {
        return parseShort(attributes.getNamedItem(name));
    }

    /**
     * Parses a short value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if the attribute is not found
     * @return the parsed short value, or the default value if the attribute is not found
     */
    default Short parseShort(NamedNodeMap attributes, String name, Short defaultValue) {
        return parseShort(attributes.getNamedItem(name), defaultValue);
    }

    /**
     * Parses an int value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed int value, or the default value if the node is null
     */
    default int parseInt(Node node, Integer defaultValue) {
        return node != null ? Integer.decode(node.getNodeValue()) : defaultValue;
    }

    /**
     * Parses an int value from the given node, using -1 as the default value.
     * @param node the XML node to parse
     * @return the parsed int value, or -1 if the node is null
     */
    default int parseInt(Node node) {
        return parseInt(node, -1);
    }

    /**
     * Parses an Integer value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed Integer value, or the default value if the node is null
     */
    default Integer parseInteger(Node node, Integer defaultValue) {
        return node != null ? Integer.decode(node.getNodeValue()) : defaultValue;
    }

    /**
     * Parses an Integer value from the given node.
     * @param node the XML node to parse
     * @return the parsed Integer value, or null if the node is null
     */
    default Integer parseInteger(Node node) {
        return parseInteger(node, null);
    }

    /**
     * Parses an Integer value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @return the parsed Integer value, or null if the attribute is not found
     */
    default Integer parseInteger(NamedNodeMap attributes, String name) {
        return parseInteger(attributes.getNamedItem(name));
    }

    /**
     * Parses an Integer value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if the attribute is not found
     * @return the parsed Integer value, or the default value if the attribute is not found
     */
    default Integer parseInteger(NamedNodeMap attributes, String name, Integer defaultValue) {
        return parseInteger(attributes.getNamedItem(name), defaultValue);
    }

    /**
     * Parses a Long value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed Long value, or the default value if the node is null
     */
    default Long parseLong(Node node, Long defaultValue) {
        return node != null ? Long.decode(node.getNodeValue()) : defaultValue;
    }

    /**
     * Parses a Long value from the given node.
     * @param node the XML node to parse
     * @return the parsed Long value, or null if the node is null
     */
    default Long parseLong(Node node) {
        return parseLong(node, null);
    }

    /**
     * Parses a Long value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @return the parsed Long value, or null if the attribute is not found
     */
    default Long parseLong(NamedNodeMap attributes, String name) {
        return parseLong(attributes.getNamedItem(name));
    }

    /**
     * Parses a Long value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if the attribute is not found
     * @return the parsed Long value, or the default value if the attribute is not found
     */
    default Long parseLong(NamedNodeMap attributes, String name, Long defaultValue) {
        return parseLong(attributes.getNamedItem(name), defaultValue);
    }

    /**
     * Parses a float value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed float value, or the default value if the node is null
     */
    default Float parseFloat(Node node, Float defaultValue) {
        return node != null ? Float.valueOf(node.getNodeValue()) : defaultValue;
    }

    /**
     * Parses a float value from the given node.
     * @param node the XML node to parse
     * @return the parsed float value, or null if the node is null
     */
    default Float parseFloat(Node node) {
        return parseFloat(node, null);
    }

    /**
     * Parses a float value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @return the parsed float value, or null if the attribute is not found
     */
    default Float parseFloat(NamedNodeMap attributes, String name) {
        return parseFloat(attributes.getNamedItem(name));
    }

    /**
     * Parses a float value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if the attribute is not found
     * @return the parsed float value, or the default value if the attribute is not found
     */
    default Float parseFloat(NamedNodeMap attributes, String name, Float defaultValue) {
        return parseFloat(attributes.getNamedItem(name), defaultValue);
    }

    /**
     * Parses a double value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed double value, or the default value if the node is null
     */
    default Double parseDouble(Node node, Double defaultValue) {
        return node != null ? Double.valueOf(node.getNodeValue()) : defaultValue;
    }

    /**
     * Parses a double value from the given node.
     * @param node the XML node to parse
     * @return the parsed double value, or null if the node is null
     */
    default Double parseDouble(Node node) {
        return parseDouble(node, null);
    }

    /**
     * Parses a double value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @return the parsed double value, or null if the attribute is not found
     */
    default Double parseDouble(NamedNodeMap attributes, String name) {
        return parseDouble(attributes.getNamedItem(name));
    }

    /**
     * Parses a double value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if the attribute is not found
     * @return the parsed double value, or the default value if the attribute is not found
     */
    default Double parseDouble(NamedNodeMap attributes, String name, Double defaultValue) {
        return parseDouble(attributes.getNamedItem(name), defaultValue);
    }

    /**
     * Parses a String value from the given node.
     * @param node the XML node to parse
     * @param defaultValue the default value to return if the node is null
     * @return the parsed String value, or the default value if the node is null
     */
    default String parseString(Node node, String defaultValue) {
        return node != null ? node.getNodeValue() : defaultValue;
    }

    /**
     * Parses a String value from the given node.
     * @param node the XML node to parse
     * @return the parsed String value, or null if the node is null
     */
    default String parseString(Node node) {
        return parseString(node, null);
    }

    /**
     * Parses a String value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @return the parsed String value, or null if the attribute is not found
     */
    default String parseString(NamedNodeMap attributes, String name) {
        return parseString(attributes.getNamedItem(name));
    }

    /**
     * Parses a String value from the specified attribute in the given attributes map.
     * @param attributes the attributes map
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if the attribute is not found
     * @return the parsed String value, or the default value if the attribute is not found
     */
    default String parseString(NamedNodeMap attributes, String name, String defaultValue) {
        return parseString(attributes.getNamedItem(name), defaultValue);
    }

    /**
     * Parses an enum value from the given node.
     * @param <T> the enum type
     * @param node the XML node to parse
     * @param enumClass the class of the enum type
     * @param defaultValue the default value to return if parsing fails
     * @return the parsed enum value, or the default value if parsing fails
     */
    default <T extends Enum<T>> T parseEnum(Node node, Class<T> enumClass, T defaultValue) {
        if (node == null) {
            return defaultValue;
        }

        try {
            return Enum.valueOf(enumClass, node.getNodeValue());
        } catch (IllegalArgumentException e) {
            LoggerFactory.getLogger(getClass())
                    .warn(
                            "Invalid value for node: {}, specified value: {} should be an enum of type \"{}\". Using"
                                    + " default value: {}",
                            node.getNodeName(),
                            node.getNodeValue(),
                            enumClass.getSimpleName(),
                            defaultValue);
            return defaultValue;
        }
    }

    /**
     * Parses an enum value from the given node.
     * @param <T> the enum type
     * @param node the XML node to parse
     * @param enumClass the class of the enum type
     * @return the parsed enum value, or null if parsing fails
     */
    default <T extends Enum<T>> T parseEnum(Node node, Class<T> enumClass) {
        return parseEnum(node, enumClass, null);
    }

    /**
     * Parses an enum value from the specified attribute in the given attributes map.
     * @param <T> the enum type
     * @param attributes the attributes map
     * @param enumClass the class of the enum type
     * @param name the name of the attribute to parse
     * @return the parsed enum value, or null if the attribute is not found or parsing fails
     */
    default <T extends Enum<T>> T parseEnum(NamedNodeMap attributes, Class<T> enumClass, String name) {
        return parseEnum(attributes.getNamedItem(name), enumClass);
    }

    /**
     * Parses an enum value from the specified attribute in the given attributes map.
     * @param <T> the enum type
     * @param attributes the attributes map
     * @param enumClass the class of the enum type
     * @param name the name of the attribute to parse
     * @param defaultValue the default value to return if parsing fails
     * @return the parsed enum value, or the default value if parsing fails
     */
    default <T extends Enum<T>> T parseEnum(NamedNodeMap attributes, Class<T> enumClass, String name, T defaultValue) {
        return parseEnum(attributes.getNamedItem(name), enumClass, defaultValue);
    }

    /**
     * Parses all attributes from the given node into a map.
     * @param node the XML node to parse
     * @return a map containing all attributes of the node as key-value pairs
     */
    default Map<String, Object> parseAttributes(Node node) {
        final NamedNodeMap attributes = node.getAttributes();
        final Map<String, Object> attributeMap = new LinkedHashMap<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Node attribute = attributes.item(i);
            attributeMap.put(attribute.getNodeName(), attribute.getNodeValue());
        }

        return attributeMap;
    }

    /**
     * Applies an action to each child node.
     * @param node the parent XML node
     * @param action the action to perform on each child node
     */
    default void forEach(Node node, Consumer<Node> action) {
        forEach(node, _ -> true, action);
    }

    /**
     * Applies an action to each child node with a matching name.
     * @param node the parent XML node
     * @param nodeName the name of the child nodes to match
     * @param action the action to perform on each matching child node
     */
    default void forEach(Node node, String nodeName, Consumer<Node> action) {
        forEach(node, child -> nodeName.equalsIgnoreCase(child.getNodeName()), action);
    }

    /**
     * Applies an action to each child node that meets a specified filter condition.
     * @param node the parent XML node
     * @param filter a filter to select specific child nodes
     * @param action the action to perform on each matching child node
     */
    default void forEach(Node node, Predicate<Node> filter, Consumer<Node> action) {
        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node childNode = children.item(i);
            if (filter.test(childNode)) {
                action.accept(childNode);
            }
        }
    }

    /**
     * Checks if the specified file is a valid XML file.
     * @param file the file to check
     * @return true if the file is an XML file and exists, false otherwise
     */
    default boolean isValidXmlFile(File file) {
        return (file != null) && file.isFile() && file.getName().toLowerCase().endsWith(".xml");
    }

    /**
     * Checks if XML validation is enabled.
     * @return {@code true} if validation is enabled, {@code false} otherwise.
     */
    default boolean isValidating() {
        return true;
    }

    /**
     * Checks if a node is of element type.
     * @param node the XML node to check
     * @return {@code true} if the node is an element, {@code false} otherwise
     */
    static boolean isNode(Node node) {
        return node.getNodeType() == Node.ELEMENT_NODE;
    }
}
