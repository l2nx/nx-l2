/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.report.model.RunEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentProbeTest {
    private static final String WINDOWS_PROCESSOR = "AMD64 Family 25 Model 33";

    @TempDir
    Path root;

    @Nested
    class Collect {
        @Test
        void collect_shouldReadTheCpuModelAndCpusetFromProc_whenOnLinux() throws IOException {
            final Path cpuInfo = Files.writeString(root.resolve("cpuinfo"), """
                processor\t: 0
                vendor_id\t: AuthenticAMD
                model name\t: AMD Ryzen 5 5600X 6-Core Processor
                processor\t: 1
                model name\t: AMD Ryzen 5 5600X 6-Core Processor
                """);
            final Path status = Files.writeString(root.resolve("status"), """
                Name:\tjava
                Cpus_allowed:\t3f
                Cpus_allowed_list:\t0-5
                """);

            final RunEnvironment actual = EnvironmentProbe.collect(cpuInfo, status, system(), null, 6);

            assertThat(actual)
                    .isEqualTo(RunEnvironment.builder()
                            .operatingSystem("Linux amd64")
                            .kernel("6.8.0-45-generic")
                            .cpuModel("AMD Ryzen 5 5600X 6-Core Processor")
                            .logicalCores(6)
                            .cpuset("0-5")
                            .jvm("OpenJDK 64-Bit Server VM 25.0.1+8")
                            .build());
        }

        @Test
        void collect_shouldTakeTheWindowsProcessorAndNoCpuset_whenThereIsNoProc() {
            final RunEnvironment actual = EnvironmentProbe.collect(
                    root.resolve("no-cpuinfo"), root.resolve("no-status"), system(), WINDOWS_PROCESSOR, 12);

            assertThat(actual.cpuModel()).isEqualTo(WINDOWS_PROCESSOR);
            assertThat(actual.cpuset()).isNull();
        }

        @Test
        void collect_shouldLeaveTheCpuModelOut_whenTheFileDoesNotNameIt() throws IOException {
            final Path cpuInfo = Files.writeString(root.resolve("cpuinfo"), "processor\t: 0\nmodel name\t:\n");

            final RunEnvironment actual =
                    EnvironmentProbe.collect(cpuInfo, root.resolve("no-status"), system(), null, 1);

            assertThat(actual.cpuModel()).isNull();
        }
    }

    private static Properties system() {
        final Properties system = new Properties();
        system.setProperty("os.name", "Linux");
        system.setProperty("os.arch", "amd64");
        system.setProperty("os.version", "6.8.0-45-generic");
        system.setProperty("java.vm.name", "OpenJDK 64-Bit Server VM");
        system.setProperty("java.runtime.version", "25.0.1+8");
        return system;
    }
}
