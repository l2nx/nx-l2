/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LoadApplication {
    public static void main(String[] args) {
        // The command is done once the context has started; the exit code carries the gate verdict.
        System.exit(SpringApplication.exit(SpringApplication.run(LoadApplication.class, args)));
    }
}
