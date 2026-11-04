/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import lombok.Builder;

/**
 * The machine a run was measured on, as the harness sees it; runs on different ones compare nothing. A value is
 * {@code null} when the platform does not tell it.
 *
 * @param kernel the kernel release on Linux, the system version elsewhere
 * @param cpuset in the kernel's list format
 */
@Builder(toBuilder = true)
public record RunEnvironment(
        String operatingSystem, String kernel, String cpuModel, int logicalCores, String cpuset, String jvm) {}
