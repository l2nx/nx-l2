/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountDataRepository
{
	private static final String UPSERT = "INSERT INTO account_data VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value=?";
	
	private final JdbcClient jdbcClient;
	
	public AccountDataRepository(JdbcClient jdbcClient)
	{
		this.jdbcClient = jdbcClient;
	}
	
	public void save(String accountName, String var, String value)
	{
		jdbcClient.sql(UPSERT).param(accountName).param(var).param(value).param(value).update();
	}
}
