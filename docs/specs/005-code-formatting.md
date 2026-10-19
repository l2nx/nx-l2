# 005. Code formatting with palantir-java-format

**One-time:** archived when the formatter runs in nx-l2, the whole tree is reformatted, the versions are aligned
across the platform, and the lasting facts are folded into spec 001 §5, `CONTRIBUTING.md` and `CLAUDE.md`.

## 1. Why

The imported Mobius tree keeps the upstream style (tabs, braces on their own line), while every other L2NX Java repo
is formatted with palantir-java-format. The global Claude Code formatting hook skips nx-l2 for that reason, so new
code here is formatted by hand and drifts. nx-l2 is refactored in place and keeps no line-level parity with Mobius
(upstream fixes are ported by hand, §10 of spec 001), so the upstream style protects nothing.

Decision: nx-l2 takes palantir-java-format like the rest of the platform, with one full reformat commit hidden from
`git blame`. Rejected: palantir through the ratchet only (the style would stay mixed for years and every functional
diff would carry reformatting); an Eclipse formatter keeping the Mobius style (a second formatter on the platform,
a second hook).

## 2. Versions

One palantir-java-format version for the hook and every Spotless consumer; otherwise the hook and `spotlessCheck`
format the same file differently. **Spotless 8.10.4, palantir-java-format 2.102.0** (latest releases on
2026-10-19). The hook and the other twelve Spotless repos run them since 2026-10-19 (steps 1 and 2 below are done);
nx-l2 adopts the same versions.

## 3. Order

Dependencies first: the hook and the existing repos are aligned before nx-l2 adopts the formatter, so nx-l2 is
reformatted by the version everyone else already runs.

1. **Hook** (`~/.claude`), done: the jar is palantir 2.102.0. The nx-l2 exclusion stays until nx-l2 is reformatted
   (step 3): without it the hook would reformat every file a functional change touches.
2. **The other Spotless repos**, done: one `build(deps)` commit each. Spotless 8 loads one build service for all
   modules, so a repo that applies the plugin per module needs `alias(libs.plugins.spotless) apply false` in the root
   build script (nx-hf-core). 2.102.0 formats 53 files of the existing code differently from 2.93.0, spread over the
   repos; the ratchet picks them up only when a change touches them.
3. **nx-l2**, in this order:
   1. `chore: normalize line endings to LF`: 21097 of 27666 tracked files are CRLF from the Mobius import. The
      platform `.gitattributes` template (§4) replaces the current partial one, then `git add --renormalize .`
      (with no unrelated edits in the working tree: renormalize stages every tracked file). The commit holds line
      endings only and goes into `.git-blame-ignore-revs` with the reformat.
   2. `build: format Java with Spotless and palantir-java-format` (§4).
   3. `style: reformat Java sources with palantir-java-format`: `spotlessApply` over the whole tree with the ratchet
      switched off locally (not committed). The commit holds formatting only. Gate:
      `./gradlew compileJava compileTestJava test spotlessCheck`.
   4. `chore: ignore the palantir reformat in git blame`: `.git-blame-ignore-revs` with the SHAs of the line-ending
      and the reformat commits. GitHub reads the file by itself; locally `git config blame.ignoreRevsFile .git-blame-ignore-revs`.
   5. Right after the reformat commit, `~/.claude/tools/format-java.js` drops `/nx-l2/` from the exclusion;
      `/bohpts` stays excluded (an upstream fork that keeps the upstream style), and the comment names bohpts only.
4. **Docs** (§5), then archive this spec.

Commits and pushes happen only on the owner's command.

## 4. nx-l2 build

- `gradle/libs.versions.toml`: versions `spotless` and `palantir`; library
  `spotless-gradle-plugin = { module = "com.diffplug.spotless:spotless-plugin-gradle", version.ref = "spotless" }`.
- `build-logic/build.gradle.kts`: `implementation(libs.spotless.gradle.plugin)`.
- `nx-l2.java-conventions.gradle.kts` applies `id("com.diffplug.spotless")`, so every Java module gets it:

  ```kotlin
  spotless {
      java {
          target("src/**/*.java")
          palantirJavaFormat(libs.findVersion("palantir").get().requiredVersion)
      }
  }
  ```

- **No ratchet.** The whole tree is formatted, so `spotlessCheck` holds every file to the format. It is part of
  `check`, so CI's `./gradlew build` fails on an unformatted file. Unlike the other repos (ratchet, local gate only),
  the check needs no git: it works on a pull request checkout without `origin/main` and from a source archive.
- **Root `build.gradle.kts`** applies `id("nx-l2.java-conventions") apply false`: Spotless 8 shares one build service
  across modules and fails when each module loads the plugin into its own classloader; the root load gives them one.
- `.gitattributes`, the platform template: `* text=auto eol=lf`, `*.bat text eol=crlf`, and a `binary` line per
  binary type the repo tracks (here only `*.jar`). It replaced the partial `*.sh` / `gradlew` / `*.bat` rules.
- `src/**/*.java` covers every source set in use: `main`, `test`, `jmh`, `testFixtures` (3184 files).
- `.git-blame-ignore-revs` lists the line-ending commit `c7d35c36a` and the reformat commit `d1d5b3cbc`.

## 5. Lasting facts folded in

- Spec 001 §5, a `Format` row.
- `CONTRIBUTING.md`, a Code style section: palantir, `spotlessApply`, the check in `build`, LF, the blame-ignore setup.
- `CLAUDE.md`: a pointer to the code style and `.git-blame-ignore-revs`.

## 6. Risks

- palantir can be slow or fail on huge Mobius files (long string concatenations, giant switches). A failing file is
  fixed at the cause, never excluded from the target.
- The reformat breaks line-level diffs against upstream Mobius. Accepted: fixes are ported by hand, and
  `docs/provenance.md` maps files, not lines.
