# Provenance

Where the code of `nx-l2` comes from, how it was imported and how its files are labelled. Project-level rules
(licenses, originality policy) live in [spec 001 §2](specs/001-project-design.md#2-legal-and-licensing); this file
holds the facts of the import.

## Upstream

| Item          | Value                                                                                        |
| ------------- | -------------------------------------------------------------------------------------------- |
| Project       | L2J Mobius, <https://gitlab.com/MobiusDevelopment/L2J_Mobius>                                |
| Chronicle     | High Five (`L2J_Mobius_CT_2.6_HighFive`)                                                     |
| Import commit | `5ddd6ff4b98` (2026-10-14)                                                                   |
| History paths | `L2J_Mobius_HighFive/` (2016-10-21 … 2017-08-29), `L2J_Mobius_CT_2.6_HighFive/` (since)      |
| Import tool   | `git filter-repo`, configuration in [`provenance/filter-repo.py`](provenance/filter-repo.py) |

The two history paths are one tree: upstream commit `ff0d4f06383` (2017-08-29, "Project reorganization") renamed
the directory without changing a file. HF history in Mobius starts on 2016-10-21; older history does not exist
upstream.

## Path mapping

Both history paths map to the same targets. `*` is `com` or `org`: upstream renamed `com.l2jmobius` to
`org.l2jmobius`, and both land in one place. Paths are rewritten in every commit, so `git log` and `git blame`
on a target path reach 2016 without `--follow`.

| Upstream (relative to the chronicle directory)                                     | `nx-l2`                                                                      |
| ---------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `java/*/l2jmobius/gameserver/`                                                     | `chronicles/high-five/server/src/main/java/app/l2nx/highfive/`               |
| `java/*/l2jmobius/{Config,Server}.java`, `status/`                                 | `chronicles/high-five/server/src/main/java/app/l2nx/highfive/`               |
| `java/*/l2jmobius/util/`                                                           | `libs/commons/src/main/java/app/l2nx/commons/util/`                          |
| `java/*/l2jmobius/log/formatter/{Accounting,Enchant,ItemLog}Formatter.java`        | `chronicles/high-five/server/src/main/java/app/l2nx/highfive/log/formatter/` |
| `java/*/l2jmobius/loginserver/`                                                    | `login/server/src/main/java/app/l2nx/login/`                                 |
| `java/*/l2jmobius/loginserver/network/` (packet layouts, reasons, key scrambling)  | `protocols/c621/src/main/java/app/l2nx/protocol/c621/`                       |
| `java/*/l2jmobius/tools/`                                                          | `login/server/src/main/java/app/l2nx/login/tools/`                           |
| `java/*/l2jmobius/commons/`                                                        | `libs/commons/src/main/java/app/l2nx/commons/`                               |
| `java/*/l2jmobius/log/` (rest)                                                     | `libs/commons/src/main/java/app/l2nx/commons/log/`                           |
| `dist/game/data/scripts/**/*.java`                                                 | `chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/**`    |
| `dist/game/data/**` (everything else)                                              | `chronicles/high-five/datapack/data/**`                                      |
| `dist/game/config/`                                                                | `chronicles/high-five/datapack/config/`                                      |
| `dist/db_installer/sql/game/`                                                      | removed at stage 3 (see "After the import", item 9)                          |
| `dist/db_installer/sql/login/`                                                     | removed at stage 3 (see "After the import", item 9)                          |
| `dist/game/*`, `dist/images/`, `dist/db_installer/*`, `dist/backup/`, `readme.txt` | `chronicles/high-five/boot/src/dist/`                                        |
| `dist/login/*`                                                                     | `login/boot/src/dist/`                                                       |

The three log formatters go to the chronicle because they import the game server; with them out, the import-time
`libs/commons` held no GPLv3 file (spec 001 §2). The root `util/` package exists only in old history: upstream moved it into
`commons/util` in `f54b892ebcd`, so it maps there and each file keeps one history.

The mapping is [`provenance/filter-repo.py`](provenance/filter-repo.py); a path it does not know fails the import.
The `libs/commons` targets are the import-time paths: the module was dissolved afterwards (item 8 under "After the
import"), and the files carry their history to the new locations.

## Stripped from history

Removed from every imported commit, not only from the tip:

- `*.jar` (third-party libraries, all versions since 2016) and `*.exe` (launch4j wrappers): dependencies come
  from the version catalog, and the only binary of a third party in the repository is the Gradle wrapper jar;
- Eclipse and Ant files: `.classpath`, `.project`, `.settings/`, `launcher/`, `build.xml`;
- the chronicle's own `.gitignore` and `.gitattributes`; the repository root has its own.

Upstream history never held geodata, client files or navmesh.

## After the import

Changes on top of the imported history, each its own commit:

1. **Package rename:** `package`, `import` and string class names `org.l2jmobius.*` / `com.l2jmobius.*` become
   `app.l2nx.*`; scripts get the `app.l2nx.highfive.scripts.` prefix. Paths were already rewritten by the
   import, so no file moves.
2. **License labels** (below).
3. **Removed:** the `.vbs` launchers, `DatabaseInstaller`, `Search`, the db_installer wrappers, `readme.txt` (patch
   notes copied from a third-party forum). The Gradle distributions (`bin/game-server`, `bin/login-server`,
   `bin/account-manager`, `bin/game-server-register`) and `compose.yaml` replace them; `java.cfg` became the
   start scripts' JVM options.
4. **Headless only:** the Swing GUI of both servers and the tools (`ui` packages, splash screen, dark theme,
   `Interface.ini`, the images) is removed; `account-manager` and `game-server-register` keep their console mode.
5. **Removed:** the restart-loop launchers `GameServer.sh`, `GameServerTask.sh`, `LoginServer.sh`,
   `LoginServerTask.sh`; exit code 2 still asks for a restart, which a supervisor performs. The `mysqldump` backup
   on shutdown (`DatabaseBackup`, the `Backup*` keys of `Database.ini`, `backup/`) is removed: backups are the
   operator's job.
6. **Rebranded:** user-visible Mobius names in runtime strings (database `l2jmobiush5` → `l2nx`, pool and thread
   names, tool banners, default announcements, server news and admin help pages). License headers, `@author` tags and
   code comments keep the upstream attribution.
7. **Stage 2, Spring Boot runtime ([spec 002](specs/002-spring-boot-runtime.md)):** the JUL logging setup
   (`log.cfg`, `console.cfg`, `ServerLogManager`, the handlers, formatters and filters of `commons/log`) gives way to
   Log4j2 (`log4j2-spring.xml` in `libs/boot`); `Database.ini` and `DatabaseConfig` to `application.yaml`; the console tools `account-manager` and
   `game-server-register` are removed.
8. **`libs/commons` dissolved:**
   - the Spring Boot glue (`ServerExit`, the shared `l2nx-defaults.yaml`, `log4j2-spring.xml`) is in `libs/boot`
     (`app.l2nx.boot`);
   - the transport and cryptography (`commons/network`, the crypt classes) are in `libs/network`
     (`app.l2nx.network`);
   - stage 4 ([spec 008](specs/008-network.md)) replaced the Mobius NIO2 transport (`Client`, `Connection`,
     `ConnectionManager`, `ReadHandler`, `WriteHandler`, the buffers and pools, `PacketExecutor`, `PacketHandler`,
     `NetworkSettings`) with the Netty transport; the game cipher moved from the chronicle's `Encryption` to
     `GameXorCipher`;
   - the game-only code (`ThreadPool`, `ConfigReader`, `IXmlReader`, `DatabaseFactory`) is in
     `chronicles/high-five/server` (`threads`, `config`, `data/xml`, `data/sql`);
   - the helpers that duplicated the JDK or commons-lang3 are removed in favour of them.
     The login server dropped its `.ini` files for `l2nx.login.*` in `application.yaml`.
9. **Stage 3, PostgreSQL ([spec 006](specs/006-database.md)):** the imported MariaDB SQL (`chronicles/high-five/datapack/sql/`,
   `login/server/sql/`) is removed. The schemas are rewritten as one Liquibase baseline per server
   (`db/liquibase/` of `login/server` and `chronicles/high-five/server`), and the static rows that were SQL (spawns of
   bosses, castles and halls, Dimensional Rift rooms) moved to datapack XML.

## License labels

| Files                                             | SPDX license       | Copyright                                   | Where        |
| ------------------------------------------------- | ------------------ | ------------------------------------------- | ------------ |
| Mobius Java with the GPL header (1822)            | `GPL-3.0-or-later` | `L2J Mobius contributors` (header has none) | file header  |
| Mobius Java with the MIT header (1372)            | `MIT`              | the header's `Copyright (c) 2013 L2jMobius` | file header  |
| Mobius files without a header (data, SQL, config) | `MIT`              | `L2J Mobius contributors`                   | `REUSE.toml` |
| Files written in `nx-l2`                          | `Apache-2.0`       | `2026 L2NX contributors`                    | file header  |

- **The SPDX lines go inside the existing header comment;** the upstream text stays as is.
- **Headerless files are MIT** by the owner's decision. Upstream's README says new and rewritten code is MIT, the
  project is moving to MIT, and the header governs each file; it says nothing about headerless files. The
  conservative reading would have been GPLv3 (inherited from the L2J datapack).
- **`2026 L2NX contributors` is added to a Mobius file only by a substantive change** (CONTRIBUTING.md); the
  package rename and the SPDX insertion are mechanical and add none.

## Import verification

Done once, at the import (2026-10-19):

- **Tree:** the HF tree at `5ddd6ff4b98` has 27657 files; 27634 are at their mapped path with the same blob, 23
  match a stripped pattern, none differs, and the import adds no other file.
- **History:** 2001 commits, the oldest HF change on 2016-10-21. Upstream has 2027 HF commits; 30 touched only
  stripped files, and 4 upstream commits that were already empty for HF (the 2015 root commit and folder cleanups)
  stay as they are.
- **Modules:** `commons` (since dissolved), `login/server`, `high-five/server` and `high-five/scripts` compile separately along the
  module boundaries after the package rename.
- **Startup:** against the upstream schema the login server starts, the game server loads 539 quests and 308
  scripts, registers on the login server and listens for clients. A login with a High Five client is not checked yet.

## Porting upstream fixes

Upstream fixes are ported by hand once the structure diverges (spec 001 §10): apply the upstream change at the
mapped path and package, keep the upstream header, credit the upstream commit in the commit message.
