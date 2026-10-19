/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import app.l2nx.login.controller.AccountInfo;
import app.l2nx.login.repository.AccountRepository;
import app.l2nx.login.repository.AccountRepository.IpAuth;

class AccountRepositoryTest extends RepositoryTest
{
	private static final long NOW = 1_800_000_000_000L;
	private static final String PASSWORD = "hash";

	private final AccountRepository repository = new AccountRepository(JDBC);

	@Nested
	class FindAccountInfo
	{
		@Test
		void findAccountInfo_shouldReturnAccount_whenNotBanned()
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 3);

			final Optional<AccountInfo> actual = repository.findAccountInfo(login, NOW);

			assertThat(actual).get().extracting(AccountInfo::getLogin, AccountInfo::getAccessLevel, AccountInfo::getLastServer).containsExactly(login, 3, 1);
		}

		@ParameterizedTest(name = "ban ends {0} ms from now -> access level {1}")
		@CsvSource(
		{
			"1, -1",
			"0, -1",
			"-1, 3"
		})
		void findAccountInfo_shouldApplyTempBan(long banEndOffset, int expected)
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 3);
			insertAccountData(login, "ban_temp", Long.toString(NOW + banEndOffset));

			final Optional<AccountInfo> actual = repository.findAccountInfo(login, NOW);

			assertThat(actual).get().extracting(AccountInfo::getAccessLevel).isEqualTo(expected);
		}

		@Test
		void findAccountInfo_shouldIgnoreOtherAccountVariables()
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 3);
			insertAccountData(login, "other", Long.toString(NOW + 1));

			final Optional<AccountInfo> actual = repository.findAccountInfo(login, NOW);

			assertThat(actual).get().extracting(AccountInfo::getAccessLevel).isEqualTo(3);
		}

		@Test
		void findAccountInfo_shouldBeEmpty_whenAccountMissing()
		{
			assertThat(repository.findAccountInfo(uniqueLogin(), NOW)).isEmpty();
		}
	}

	@Nested
	class Create
	{
		@Test
		void create_shouldStoreAccountWithAccessLevelZero()
		{
			final String login = uniqueLogin();

			repository.create(login, PASSWORD, NOW, "10.0.0.1");

			assertThat(account(login)).containsEntry("password", PASSWORD).containsEntry("lastactive", NOW).containsEntry("accessLevel", 0).containsEntry("lastIP", "10.0.0.1");
		}
	}

	@Nested
	class Update
	{
		@Test
		void updateLastActive_shouldSetTimeAndAddress()
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 0);

			repository.updateLastActive(login, NOW, "10.0.0.2");

			assertThat(account(login)).containsEntry("lastactive", NOW).containsEntry("lastIP", "10.0.0.2");
		}

		@Test
		void updateLastServer_shouldSetServer()
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 0);

			repository.updateLastServer(login, 7);

			assertThat(account(login)).containsEntry("lastServer", 7);
		}

		@Test
		void updateAccessLevel_shouldSetAccessLevel()
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 0);

			repository.updateAccessLevel(login, -1);

			assertThat(account(login)).containsEntry("accessLevel", -1);
		}

		@Test
		void updateTracert_shouldSetAddressAndHops()
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 0);

			repository.updateTracert(login, "10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5");

			assertThat(account(login)).containsEntry("pcIp", "10.0.0.1").containsEntry("hop1", "10.0.0.2").containsEntry("hop2", "10.0.0.3").containsEntry("hop3", "10.0.0.4").containsEntry("hop4", "10.0.0.5");
		}
	}

	@Nested
	class UpdatePassword
	{
		@Test
		void updatePassword_shouldChangePassword_whenCurrentHashMatches()
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 0);

			final boolean actual = repository.updatePassword(login, PASSWORD, "new");

			assertThat(actual).isTrue();
			assertThat(repository.findPassword(login)).contains("new");
		}

		@Test
		void updatePassword_shouldKeepPassword_whenCurrentHashDiffers()
		{
			final String login = uniqueLogin();
			insertAccount(login, PASSWORD, 0);

			final boolean actual = repository.updatePassword(login, "wrong", "new");

			assertThat(actual).isFalse();
			assertThat(repository.findPassword(login)).contains(PASSWORD);
		}
	}

	@Nested
	class FindPassword
	{
		@Test
		void findPassword_shouldBeEmpty_whenAccountMissing()
		{
			assertThat(repository.findPassword(uniqueLogin())).isEmpty();
		}
	}

	@Nested
	class FindIpAuths
	{
		@Test
		void findIpAuths_shouldReturnRulesOfAccount()
		{
			final String login = uniqueLogin();
			insertIpAuth(login, "10.0.0.1", "allow");
			insertIpAuth(login, "10.0.0.2", "deny");
			insertIpAuth(uniqueLogin(), "10.0.0.3", "deny");

			final List<IpAuth> actual = repository.findIpAuths(login);

			assertThat(actual).extracting(IpAuth::ip, IpAuth::type).containsExactlyInAnyOrder(tuple("10.0.0.1", "allow"), tuple("10.0.0.2", "deny"));
		}
	}

	private static void insertIpAuth(String login, String ip, String type)
	{
		JDBC.sql("INSERT INTO accounts_ipauth (login, ip, type) VALUES (?, ?, ?)").param(login).param(ip).param(type).update();
	}

	private static Map<String, Object> account(String login)
	{
		return JDBC.sql("SELECT password, CAST(lastactive AS SIGNED) AS lastactive, accessLevel, lastIP, lastServer, pcIp, hop1, hop2, hop3, hop4 FROM accounts WHERE login = ?").param(login).query().singleRow();
	}
}
