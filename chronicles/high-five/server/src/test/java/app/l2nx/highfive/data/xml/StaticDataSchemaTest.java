/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Loaders only log schema errors and load a broken file partly; this validation fails on the first error.
 */
class StaticDataSchemaTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "data/RaidBossSpawns.xml, data/xsd/RaidBossSpawns.xsd",
        "data/GrandBosses.xml, data/xsd/GrandBosses.xsd",
        "data/AutoSpawns.xml, data/xsd/AutoSpawns.xsd",
        "data/DimensionalRift.xml, data/xsd/DimensionalRift.xsd",
        "data/Castles.xml, data/xsd/Castles.xsd",
        "data/Forts.xml, data/xsd/Forts.xsd",
        "data/ClanHalls.xml, data/xsd/ClanHalls.xsd",
        "data/SiegableClanHalls.xml, data/xsd/SiegableClanHalls.xsd",
        "data/Territories.xml, data/xsd/Territories.xsd"
    })
    void validate_shouldAcceptDatapackFile(String xml, String xsd) throws Exception {
        assertValid(new File(xml), xsd);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("itemFiles")
    void validate_shouldAcceptItemFile(File xml) throws Exception {
        assertValid(xml, "data/xsd/items.xsd");
    }

    static Stream<File> itemFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("data/stats/items"))) {
            return files.filter(file -> file.toString().endsWith(".xml")).sorted().map(Path::toFile).toList().stream();
        }
    }

    private static void assertValid(File xml, String xsd) throws Exception {
        final Validator validator = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new File(xsd))
                .newValidator();

        assertThatNoException().isThrownBy(() -> validator.validate(new StreamSource(xml)));
    }
}
