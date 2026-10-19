/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "app.l2nx.highfive")
public class HighFiveApplication {
    public static void main(String[] args) {
        SpringApplication.run(HighFiveApplication.class, args);
    }
}
