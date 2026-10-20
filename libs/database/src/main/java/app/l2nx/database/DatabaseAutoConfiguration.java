/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.database;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class DatabaseAutoConfiguration {
    // Static: a BeanPostProcessor has to exist before the beans it processes.
    @Bean
    @ConditionalOnClass(name = "liquibase.integration.spring.SpringLiquibase")
    public static DatabaseSchemaCheck databaseSchemaCheck(@Value("${l2nx.database.schema:#{null}}") String schema) {
        return new DatabaseSchemaCheck(schema);
    }
}
