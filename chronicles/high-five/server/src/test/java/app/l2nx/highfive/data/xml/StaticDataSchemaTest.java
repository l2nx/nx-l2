/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.File;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The loaders validate with a parser that only reports schema errors, so a broken file would load partly and still
 * pass the count checks of a single row; this validation fails on the first error.
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
        final Validator validator = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new File(xsd))
                .newValidator();

        assertThatNoException().isThrownBy(() -> validator.validate(new StreamSource(new File(xml))));
    }
}
