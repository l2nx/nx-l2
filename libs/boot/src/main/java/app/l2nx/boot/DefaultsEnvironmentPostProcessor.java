/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.boot;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Adds the defaults shared by every server as the last property source, so any key of the application wins.
 */
public class DefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String DEFAULTS = "l2nx-defaults.yaml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            for (PropertySource<?> source :
                    new YamlPropertySourceLoader().load(DEFAULTS, new ClassPathResource(DEFAULTS))) {
                environment.getPropertySources().addLast(source);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + DEFAULTS, e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
