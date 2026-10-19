/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import app.l2nx.login.controller.AccountInfo;

/**
 * The {@code accounts} table and the IP restrictions of an account in {@code accounts_ipauth}.
 */
@Repository
public class AccountRepository
{
	// account_data.value is a VARCHAR: ban_temp is compared with the current epoch milliseconds as a string.
	private static final String SELECT_ACCOUNT_INFO = "SELECT login, password, IF(? > value OR value IS NULL, accessLevel, -1) AS accessLevel, lastServer FROM accounts LEFT JOIN (account_data) ON (account_data.account_name=accounts.login AND account_data.var=\"ban_temp\") WHERE login=?";
	private static final String INSERT_ACCOUNT = "INSERT INTO accounts (login, password, lastactive, accessLevel, lastIP) values (?, ?, ?, ?, ?)";
	private static final String UPDATE_LAST_ACTIVE = "UPDATE accounts SET lastactive = ?, lastIP = ? WHERE login = ?";
	private static final String UPDATE_LAST_SERVER = "UPDATE accounts SET lastServer = ? WHERE login = ?";
	private static final String UPDATE_ACCESS_LEVEL = "UPDATE accounts SET accessLevel = ? WHERE login = ?";
	private static final String UPDATE_TRACERT = "UPDATE accounts SET pcIp = ?, hop1 = ?, hop2 = ?, hop3 = ?, hop4 = ? WHERE login = ?";
	private static final String SELECT_PASSWORD = "SELECT password FROM accounts WHERE login=?";
	private static final String UPDATE_PASSWORD_MATCHING = "UPDATE accounts SET password=? WHERE login=? AND password=?";
	private static final String SELECT_IP_AUTH = "SELECT * FROM accounts_ipauth WHERE login = ?";
	
	private final JdbcClient jdbcClient;
	
	public AccountRepository(JdbcClient jdbcClient)
	{
		this.jdbcClient = jdbcClient;
	}
	
	/**
	 * @param now epoch milliseconds; an account whose {@code ban_temp} lies after it reads with access level -1
	 */
	public Optional<AccountInfo> findAccountInfo(String login, long now)
	{
		return jdbcClient.sql(SELECT_ACCOUNT_INFO).param(Long.toString(now)).param(login).query((rs, rowNum) -> new AccountInfo(rs.getString("login"), rs.getString("password"), rs.getInt("accessLevel"), rs.getInt("lastServer"))).optional();
	}
	
	public void create(String login, String passwordHash, long lastActive, String lastIp)
	{
		jdbcClient.sql(INSERT_ACCOUNT).param(login).param(passwordHash).param(lastActive).param(0).param(lastIp).update();
	}
	
	public void updateLastActive(String login, long lastActive, String lastIp)
	{
		jdbcClient.sql(UPDATE_LAST_ACTIVE).param(lastActive).param(lastIp).param(login).update();
	}
	
	public void updateLastServer(String login, int serverId)
	{
		jdbcClient.sql(UPDATE_LAST_SERVER).param(serverId).param(login).update();
	}
	
	public void updateAccessLevel(String login, int accessLevel)
	{
		jdbcClient.sql(UPDATE_ACCESS_LEVEL).param(accessLevel).param(login).update();
	}
	
	public void updateTracert(String login, String pcIp, String hop1, String hop2, String hop3, String hop4)
	{
		jdbcClient.sql(UPDATE_TRACERT).param(pcIp).param(hop1).param(hop2).param(hop3).param(hop4).param(login).update();
	}
	
	public Optional<String> findPassword(String login)
	{
		return jdbcClient.sql(SELECT_PASSWORD).param(login).query(String.class).optional();
	}
	
	/**
	 * @return whether the password was changed: the account exists and its password hash is {@code currentHash}
	 */
	public boolean updatePassword(String login, String currentHash, String newHash)
	{
		return jdbcClient.sql(UPDATE_PASSWORD_MATCHING).param(newHash).param(login).param(currentHash).update() > 0;
	}
	
	public List<IpAuth> findIpAuths(String login)
	{
		return jdbcClient.sql(SELECT_IP_AUTH).param(login).query((rs, rowNum) -> new IpAuth(rs.getString("ip"), rs.getString("type"))).list();
	}
	
	public record IpAuth(String ip, String type)
	{
	}
}
