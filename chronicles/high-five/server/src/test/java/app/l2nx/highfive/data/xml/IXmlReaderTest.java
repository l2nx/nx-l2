/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

@ResourceLock("io.micrometer.core.instrument.Metrics.globalRegistry")
class IXmlReaderTest {
    private static final int THREADS = 4;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
    }

    @Nested
    class ParseInParallel {
        @Test
        void parseInParallel_shouldParseOnPoolThreads_andConsumeInSourceOrderOnTheCallingThread() {
            final List<Integer> sources = IntStream.range(0, 64).boxed().toList();
            final List<String> parsed = new ArrayList<>();
            final List<Thread> consumers = new ArrayList<>();

            IXmlReader.parseInParallel(
                    sources,
                    THREADS,
                    source -> source + "@" + Thread.currentThread().getName(),
                    (source, result) -> {
                        parsed.add(result);
                        consumers.add(Thread.currentThread());
                    });

            assertThat(parsed).hasSize(sources.size());
            for (int i = 0; i < sources.size(); i++) {
                assertThat(parsed.get(i)).startsWith(i + "@" + IXmlReader.PARSE_POOL + "-");
            }
            assertThat(consumers).containsOnly(Thread.currentThread());
        }

        @Test
        void parseInParallel_shouldRemoveThePoolMeters_whenThePoolCloses() {
            final List<Timer> duringParse = new ArrayList<>();

            IXmlReader.parseInParallel(
                    List.of(1),
                    THREADS,
                    source -> registry.find("executor")
                            .tag("name", IXmlReader.PARSE_POOL)
                            .timer(),
                    (source, timer) -> duringParse.add(timer));

            assertThat(duringParse).first().isNotNull();
            assertThat(registry.find("executor")
                            .tag("name", IXmlReader.PARSE_POOL)
                            .meters())
                    .isEmpty();
        }

        @Test
        void parseInParallel_shouldRethrowTheParserFailure() {
            assertThatThrownBy(() -> IXmlReader.parseInParallel(
                            List.of(1, 2),
                            THREADS,
                            source -> {
                                throw new IllegalArgumentException("bad file " + source);
                            },
                            (source, result) -> {}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("bad file");
        }
    }

    @Nested
    class ParseFile {
        @Test
        void parseFile_shouldResolveAnEntityOfALocalFile(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("local.dtd"), "<!ENTITY marker \"local\">");
            final Path file = directory.resolve("list.xml");
            Files.writeString(file, "<!DOCTYPE list SYSTEM \"local.dtd\"><list name=\"&marker;\"/>");
            final RecordingReader reader = new RecordingReader();

            reader.parseFile(file.toFile());

            assertThat(reader.names).containsExactly("local");
        }

        // The JDK built-in catalog maps this system id to a bundled DTD: a parse that still went through it would read
        // the file, and that catalog is the shared state concurrent parses race on.
        @Test
        void parseFile_shouldSkipTheFile_whenItReferencesAnEntityOfTheJdkCatalog(@TempDir Path directory)
                throws IOException {
            final Path file = directory.resolve("list.xml");
            Files.writeString(file, """
                <!DOCTYPE list PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" \
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"><list name="remote"/>\
                """);
            final RecordingReader reader = new RecordingReader();

            reader.parseFile(file.toFile());

            assertThat(reader.names).isEmpty();
        }
    }

    @Nested
    class ParseDirectory {
        @ParameterizedTest(name = "threads {0}")
        @ValueSource(ints = {1, THREADS})
        void parseDirectory_shouldApplyEveryDocumentOnTheCallingThread_inFileOrder(int threads, @TempDir Path directory)
                throws IOException {
            final List<String> expected = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                final String name = "file" + i;
                Files.writeString(directory.resolve(name + ".xml"), "<list name=\"" + name + "\"/>");
            }
            final File[] listed = directory.toFile().listFiles();
            for (File file : listed) {
                expected.add(file.getName().replace(".xml", ""));
            }
            final RecordingReader reader = new RecordingReader();

            reader.parseDirectory(directory.toFile(), false, threads);

            assertThat(reader.names).containsExactlyElementsOf(expected);
            assertThat(reader.threads).containsOnly(Thread.currentThread());
        }

        @Test
        void parseDirectory_shouldReadTheDatapackAlike_withOneThreadAndWithAPool() {
            final File directory = new File(".", "data/stats/npcs");
            final DatapackReader sequential = new DatapackReader();
            final DatapackReader parallel = new DatapackReader();

            sequential.parseDirectory(directory, false, 1);
            parallel.parseDirectory(directory, false, THREADS);

            assertThat(sequential.files).hasSizeGreaterThan(THREADS * 2);
            assertThat(parallel.files).containsExactlyElementsOf(sequential.files);
        }
    }

    private static final class RecordingReader implements IXmlReader {
        private final List<String> names = new ArrayList<>();
        private final List<Thread> threads = new ArrayList<>();

        @Override
        public void load() {}

        @Override
        public boolean isValidating() {
            return false;
        }

        @Override
        public void parseDocument(Document document, File file) {
            names.add(document.getDocumentElement().getAttribute("name"));
            threads.add(Thread.currentThread());
        }
    }

    private static final class DatapackReader implements IXmlReader {
        private final List<String> files = new ArrayList<>();

        @Override
        public void load() {}

        @Override
        public void parseDocument(Document document, File file) {
            files.add(file.getName() + ":"
                    + document.getDocumentElement().getChildNodes().getLength());
        }
    }
}
