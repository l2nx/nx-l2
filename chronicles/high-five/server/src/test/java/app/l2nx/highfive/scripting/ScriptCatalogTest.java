/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.scripting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScriptCatalogTest
{
	private static final String FIXTURE = "app.l2nx.highfive.scripting.fixture";

	@Nested
	class Scripts
	{
		@Test
		void scripts_shouldListTopLevelClassesWithStaticMainSortedByName()
		{
			final ScriptCatalog catalog = new ScriptCatalog(FIXTURE, Map.of());

			assertThat(catalog.scripts()).containsExactly("ai.Outer", "handlers.MasterHandler", "quests.Alpha.Alpha", "quests.Beta.Beta");
		}

		@Test
		void scripts_shouldSkipScript_whenExcluded()
		{
			final ScriptCatalog catalog = new ScriptCatalog(FIXTURE, Map.of("handlers", Set.of(), "Beta", Set.of()));

			assertThat(catalog.scripts()).containsExactly("ai.Outer", "quests.Alpha.Alpha");
		}
	}

	@Nested
	class IsExcluded
	{
		private final ScriptCatalog catalog = new ScriptCatalog(FIXTURE, Map.of("handlers", Set.of(), "ForgeOfTheGods", Set.of("Valakas.java"), "Q00001_Old.java", Set.of()));

		@ParameterizedTest(name = "{0} -> {1}")
		@CsvSource({
			"handlers.MasterHandler, true",
			"handlers.chat.AdminQuest, true",
			"ai.areas.ForgeOfTheGods.Antharas, true",
			"ai.areas.ForgeOfTheGods.Valakas, false",
			"quests.Q00001_Old, true",
			"quests.Q00002_New.Q00002_New, false",
		})
		void isExcluded_shouldFollowScriptsXml(String script, boolean expected)
		{
			assertThat(catalog.isExcluded(script)).isEqualTo(expected);
		}
	}

	@Nested
	class FolderOf
	{
		private final ScriptCatalog catalog = new ScriptCatalog(FIXTURE, Map.of());

		@ParameterizedTest(name = "{0} -> {1}")
		@CsvSource({
			"app.l2nx.highfive.scripting.fixture.quests.Alpha.Alpha, quests/Alpha",
			"app.l2nx.highfive.scripting.fixture.handlers.MasterHandler, handlers",
			"app.l2nx.highfive.other.Some, app/l2nx/highfive/other",
		})
		void folderOf_shouldReturnPackagePathRelativeToRoot(String className, String expected)
		{
			assertThat(catalog.folderOf(className)).isEqualTo(expected);
		}
	}

	@Nested
	class ToScriptName
	{
		@ParameterizedTest(name = "{0} -> {1}")
		@CsvSource({
			"quests.QuestMasterHandler, quests.QuestMasterHandler",
			"quests/QuestMasterHandler.java, quests.QuestMasterHandler",
			"ai\\others\\Epidos.java, ai.others.Epidos",
		})
		void toScriptName_shouldAcceptNameOrMobiusPath(String name, String expected)
		{
			assertThat(ScriptCatalog.toScriptName(name)).isEqualTo(expected);
		}
	}
}
