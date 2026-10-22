/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.AutoSpawnGroup;
import java.io.File;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

class AutoSpawnDataTest {

    private static final AutoSpawnData DATA = AutoSpawnData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedGroup() {
            assertThat(DATA.getGroups()).hasSize(129);
        }

        @Test
        void load_shouldReadEveryImportedLocation() {
            final int actual = DATA.getGroups().stream()
                    .mapToInt(group -> group.locations().size())
                    .sum();

            assertThat(actual).isEqualTo(193);
        }

        @Test
        void load_shouldReadGroupAttributes() {
            final AutoSpawnGroup actual = DATA.getGroups().getFirst();

            assertThat(actual.npcId()).isEqualTo(31113);
            assertThat(actual.initialDelaySeconds()).isEqualTo(-1);
            assertThat(actual.broadcastSpawn()).isTrue();
            assertThat(actual.randomSpawn()).isTrue();
            assertThat(actual.locations()).hasSize(8);
        }

        @Test
        void load_shouldReadDelaysInSeconds_whenGroupDespawns() {
            final AutoSpawnGroup actual = DATA.getGroups().stream()
                    .filter(group -> group.npcId() == 32335)
                    .findFirst()
                    .orElseThrow();

            assertThat(actual.respawnDelaySeconds()).isEqualTo(120);
            assertThat(actual.despawnDelaySeconds()).isEqualTo(120);
        }
    }

    @Nested
    class ParseDocument {
        private static Document document(String groups) throws Exception {
            return DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader("<list>" + groups + "</list>")));
        }

        private static String group(int npcId, int respawnDelaySeconds, int despawnDelaySeconds) {
            return "<group npcId=\"" + npcId + "\" count=\"1\" initialDelaySeconds=\"-1\" respawnDelaySeconds=\""
                    + respawnDelaySeconds + "\" despawnDelaySeconds=\"" + despawnDelaySeconds
                    + "\" broadcastSpawn=\"false\" randomSpawn=\"false\"><location x=\"0\" y=\"0\" z=\"0\" heading=\"0\"/>"
                    + "</group>";
        }

        @Test
        void parseDocument_shouldSkipGroup_whenItDespawnsWithZeroRespawnDelay() throws Exception {
            final AutoSpawnData data = new AutoSpawnData();
            final int before = data.getGroups().size();

            data.parseDocument(document(group(1, 0, 60) + group(2, 60, 60) + group(3, 0, 0)), new File("test.xml"));

            assertThat(data.getGroups()).hasSize(before + 2);
            assertThat(data.getGroups().subList(before, before + 2))
                    .extracting(AutoSpawnGroup::npcId)
                    .containsExactly(2, 3);
        }
    }
}
