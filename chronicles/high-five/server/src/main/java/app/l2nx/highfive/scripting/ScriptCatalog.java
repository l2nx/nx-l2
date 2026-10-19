/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.scripting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;

/**
 * Finds the compiled scripts under a root package. A script is a top-level class with {@code public static void main(String[])}; it is named
 * relative to the root package ({@code quests.QuestMasterHandler}), the way Mobius named script files relative to the scripts folder.
 */
public class ScriptCatalog
{
	private final String rootPackage;
	private final Map<String, Set<String>> exclusions;

	/**
	 * @param exclusions {@code config/Scripts.xml}: an excluded folder or file name mapped to the names re-included under it
	 */
	public ScriptCatalog(String rootPackage, Map<String, Set<String>> exclusions)
	{
		this.rootPackage = rootPackage;
		this.exclusions = exclusions;
	}

	public List<String> scripts()
	{
		final List<String> scripts = new ArrayList<>();
		try (ScanResult scan = new ClassGraph().enableClasspath().enableClassInfo().enableMethodInfo().acceptPackages(rootPackage).scan())
		{
			for (ClassInfo classInfo : scan.getAllClasses())
			{
				if (classInfo.isNestedClass() || !hasMain(classInfo))
				{
					continue;
				}

				final String script = classInfo.getName().substring(rootPackage.length() + 1);
				if (!isExcluded(script))
				{
					scripts.add(script);
				}
			}
		}

		Collections.sort(scripts);
		return scripts;
	}

	/**
	 * Applies {@code Scripts.xml} as Mobius did to the file path: package segments are folders, the simple name plus {@code .java} is the file.
	 * @return {@code true} if an excluded folder or file covers the script and no {@code include} under it names a later part
	 */
	public boolean isExcluded(String script)
	{
		final List<String> parts = new ArrayList<>(Arrays.asList(script.split("\\.")));
		parts.set(parts.size() - 1, parts.get(parts.size() - 1) + ".java");
		for (int i = 0; i < parts.size(); i++)
		{
			final Set<String> includes = exclusions.get(parts.get(i));
			if (includes != null)
			{
				for (int j = i + 1; j < parts.size(); j++)
				{
					if (includes.contains(parts.get(j)))
					{
						return false;
					}
				}

				return true;
			}
		}

		return false;
	}

	/**
	 * @param className a fully qualified class name; a class outside the root package keeps its whole package
	 * @return the {@code /}-separated folder relative to the root package; where the script keeps its HTML under {@code data/scripts}
	 */
	public String folderOf(String className)
	{
		final String relative = className.startsWith(rootPackage + ".") ? className.substring(rootPackage.length() + 1) : className;
		final int lastDot = relative.lastIndexOf('.');
		return lastDot < 0 ? "" : relative.substring(0, lastDot).replace('.', '/');
	}

	/**
	 * @param name a script name ({@code quests.QuestMasterHandler}) or a Mobius script path ({@code quests/QuestMasterHandler.java})
	 */
	public static String toScriptName(String name)
	{
		final String withoutExtension = name.endsWith(".java") ? name.substring(0, name.length() - 5) : name;
		return withoutExtension.replace('\\', '.').replace('/', '.');
	}

	private static boolean hasMain(ClassInfo classInfo)
	{
		for (MethodInfo method : classInfo.getDeclaredMethodInfo("main"))
		{
			if (method.isPublic() && method.isStatic() && (method.getParameterInfo().size() == 1) && "java.lang.String[]".equals(method.getParameterInfo().get(0).getTypeDescriptor().toString()))
			{
				return true;
			}
		}

		return false;
	}
}
