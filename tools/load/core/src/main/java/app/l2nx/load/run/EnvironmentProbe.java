/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.report.model.RunEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** What the JVM does not tell comes from {@code /proc}: the gate runs on Linux. */
final class EnvironmentProbe {
    private static final Path CPU_INFO = Path.of("/proc/cpuinfo");
    private static final Path SELF_STATUS = Path.of("/proc/self/status");
    private static final String CPU_MODEL_KEY = "model name";
    private static final String CPUSET_KEY = "Cpus_allowed_list";
    // Windows names the processor only here, as family, model and stepping.
    private static final String WINDOWS_PROCESSOR = "PROCESSOR_IDENTIFIER";

    private EnvironmentProbe() {}

    static RunEnvironment collect() {
        return collect(
                CPU_INFO,
                SELF_STATUS,
                System.getProperties(),
                System.getenv(WINDOWS_PROCESSOR),
                Runtime.getRuntime().availableProcessors());
    }

    static RunEnvironment collect(
            Path cpuInfo, Path selfStatus, Properties system, String windowsProcessor, int logicalCores) {
        final String cpuModel = value(cpuInfo, CPU_MODEL_KEY);
        return RunEnvironment.builder()
                .operatingSystem(system.getProperty("os.name") + " " + system.getProperty("os.arch"))
                .kernel(system.getProperty("os.version"))
                .cpuModel(cpuModel == null ? windowsProcessor : cpuModel)
                .logicalCores(logicalCores)
                .cpuset(value(selfStatus, CPUSET_KEY))
                .jvm(system.getProperty("java.vm.name") + " " + system.getProperty("java.runtime.version"))
                .build();
    }

    /** The first {@code key: value} line of a {@code /proc} file; {@code null} when there is no such file or line. */
    private static String value(Path file, String key) {
        if (!Files.isReadable(file)) {
            return null;
        }
        final List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + file, e);
        }
        for (String line : lines) {
            final int separator = line.indexOf(':');
            if (separator > 0 && line.substring(0, separator).strip().equals(key)) {
                final String value = line.substring(separator + 1).strip();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
