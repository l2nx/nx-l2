/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.persistence.value.PersistenceAutoConfiguration;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.core.persistence.value.ValueTransactions;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.simple.JdbcClient;

@AutoConfiguration(
        after = {PersistenceAutoConfiguration.class, TaskSchedulingAutoConfiguration.class},
        afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnBean({
    MailParticipants.class,
    MailItems.class,
    MailItemStore.class,
    ValueTransactions.class,
    ValueTransactionSubmitter.class,
    BackgroundQueries.class,
    JdbcClient.class
})
@EnableConfigurationProperties(MailSettings.class)
@Import(MailAutoConfiguration.Components.class)
public class MailAutoConfiguration {
    /** A component scan cannot sit behind a bean condition and the handlers are package-private, so they are imported. */
    static final class Components implements ImportSelector, ResourceLoaderAware {
        private ResourceLoader resourceLoader;

        @Override
        public void setResourceLoader(ResourceLoader resourceLoader) {
            this.resourceLoader = resourceLoader;
        }

        @Override
        public String[] selectImports(AnnotationMetadata metadata) {
            final ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(true);
            scanner.setResourceLoader(resourceLoader);
            scanner.addExcludeFilter(new AnnotationTypeFilter(AutoConfiguration.class));
            return scanner.findCandidateComponents(MailAutoConfiguration.class.getPackageName()).stream()
                    .map(BeanDefinition::getBeanClassName)
                    .sorted()
                    .toArray(String[]::new);
        }
    }
}
