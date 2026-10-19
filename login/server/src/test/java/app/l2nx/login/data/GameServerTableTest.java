/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.repository.GameServerRepository;

@Execution(ExecutionMode.SAME_THREAD)
class GameServerTableTest
{
	@TempDir
	private Path datapackRoot;
	
	@Nested
	class Load
	{
		@Test
		void load_shouldReadServerNames_fromDatapackRoot() throws IOException
		{
			Files.createDirectories(datapackRoot.resolve("data"));
			Files.writeString(datapackRoot.resolve("data/servername.xml"), """
				<?xml version="1.0" encoding="UTF-8"?>
				<servers_list xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="servername.xsd">
					<server id="1" name="Bartz" />
					<server id="9" name="Teon (EURO)" />
				</servers_list>
				""");
			final GameServerTable table = new GameServerTable(mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
			
			table.load();
			
			assertThat(table.getServerNames()).containsOnly(entry(1, "Bartz"), entry(9, "Teon (EURO)"));
		}
		
		@Test
		void load_shouldLeaveServerNamesEmpty_whenFileMissing()
		{
			final GameServerTable table = new GameServerTable(mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
			
			table.load();
			
			assertThat(table.getServerNames()).isEmpty();
		}
	}
}
