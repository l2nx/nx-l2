# 005. Code formatting

## 1. Decision

- **Java is formatted with palantir-java-format**, the formatter of every other L2NX Java repo, through Spotless.
- **The whole tree is held to the format.** `spotlessCheck` is part of `check`, so `./gradlew build` (and CI) fails
  on an unformatted file. No ratchet: the check needs no git, so it works on a pull request checkout and from a
  source archive.
- **Line endings are LF** for every text file, Windows scripts (`*.bat`) excepted.
- **Bulk commits stay in `git blame`.** The repository keeps no blame ignore list.

## 2. Why

The imported Mobius tree kept the upstream style (tabs, braces on their own line). nx-l2 is refactored in place and
keeps no line-level parity with Mobius (upstream fixes are ported by hand, spec 001 §10), so the upstream style
protects nothing, while a second style on the platform means hand formatting and drift.

Rejected:

- **palantir through a ratchet only**, as in the other repos: the style stays mixed for years and every functional
  diff carries reformatting. The other repos use the ratchet because their trees are not fully formatted; nx-l2 is.
- **An Eclipse formatter keeping the Mobius style**: a second formatter and a second editor hook on the platform.

The reformat broke line-level diffs against upstream Mobius. Accepted: fixes are ported by hand, and
`docs/provenance.md` maps files, not lines.

## 3. Build

- `gradle/libs.versions.toml`: versions `spotless` and `palantir`; library `spotless-gradle-plugin`.
- `build-logic/build.gradle.kts` puts the plugin on the convention classpath; `nx-l2.java-conventions` applies
  `com.diffplug.spotless` with `java { target("src/**/*.java"); palantirJavaFormat(<palantir>) }`, so every Java
  module and every source set (`main`, `test`, `jmh`, `testFixtures`) is covered.
- The root `build.gradle.kts` applies `id("nx-l2.java-conventions") apply false`. Spotless 8 shares one build service
  across modules and fails when each module loads the plugin into its own classloader; the root load gives them one.
- `./gradlew spotlessApply` formats; the Claude Code hook formats each edited `.java` file on its own.

## 4. Versions

One palantir-java-format version for the Claude Code hook (`~/.claude/tools/fetch-format-jar.js`) and every Spotless
consumer of the platform; otherwise the hook and `spotlessCheck` format the same file differently. Renovate tracks
`palantir` (a bare version key) through a regex manager of the shared preset and moves the catalog; the hook jar is
bumped separately, so a palantir update is merged together with the hook bump. Current: Spotless 8.11.0,
palantir-java-format 2.103.0.

## 5. Line endings and blame

- `.gitattributes`: `* text=auto eol=lf`, `*.bat text eol=crlf`, and a `binary` line per binary type the repo tracks
  (today only `*.jar`). The same template is used across the platform repos.
- **No `.git-blame-ignore-revs`.** The LF normalization `c7d35c36a`, the full reformat `d1d5b3cbc` and later bulk
  changes (a palantir bump that rewraps many files, a code conversion) stay in `git blame` under their author. Owner's
  choice: the person who carried out the change is credited on the lines it touched.
