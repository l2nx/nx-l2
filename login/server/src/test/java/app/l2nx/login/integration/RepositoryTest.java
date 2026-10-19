/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One database for every repository test class, with the schema of {@code login/server/sql}. Tests use their own account
 * names, so the classes may run concurrently.
 */
abstract class RepositoryTest
{
	// MariaDB reached through MySQL Connector/J, the driver the server ships with.
	private static final MySQLContainer DATABASE = new MySQLContainer(DockerImageName.parse("mariadb:11.8").asCompatibleSubstituteFor("mysql"));
	
	protected static final JdbcClient JDBC;
	
	static
	{
		DATABASE.start();
		final DriverManagerDataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
		final ResourceDatabasePopulator schema = new ResourceDatabasePopulator();
		try (Stream<Path> scripts = Files.list(Path.of(System.getProperty("l2nx.test.login-sql-dir"))))
		{
			scripts.filter(script -> script.toString().endsWith(".sql")).sorted().forEach(script -> schema.addScript(new FileSystemResource(script)));
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		schema.execute(dataSource);
		JDBC = JdbcClient.create(dataSource);
	}
	
	protected static String uniqueLogin()
	{
		return "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
	}
	
	protected static void insertAccount(String login, String password, int accessLevel)
	{
		JDBC.sql("INSERT INTO accounts (login, password, accessLevel) VALUES (?, ?, ?)").param(login).param(password).param(accessLevel).update();
	}
	
	protected static void insertAccountData(String login, String var, String value)
	{
		JDBC.sql("INSERT INTO account_data (account_name, var, value) VALUES (?, ?, ?)").param(login).param(var).param(value).update();
	}
}
