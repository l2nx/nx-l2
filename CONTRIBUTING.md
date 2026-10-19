# Contributing to nx-l2

> External pull requests are not accepted yet. They open once contribution sign-off is in place
> ([#7](https://github.com/l2nx/nx-l2/issues/7)). Issues and discussion are welcome.

## Originality

- **No decompiled, leaked or proprietary code.** Not from game clients, not from private or commercial servers.
- **Behaviour may be re-implemented, code may not be copied.** Knowing how a mechanic works on another server is fine;
  the implementation is written from scratch.
- **Code from other open-source projects** comes in only under a license compatible with Apache 2.0 (MIT, BSD, Apache
  2.0), with its copyright and license kept. GPL code from other projects is never copied: it would bind the build to
  GPL again.
- **No game client, client assets, geodata or baked navmesh** in the repository. Tools are shipped instead.

## Licensing of contributions

Inbound = outbound: a contribution comes under the license of the file it touches. A new file is Apache 2.0.

| File                           | License    | What you do                                           |
| ------------------------------ | ---------- | ----------------------------------------------------- |
| new, or rewritten from scratch | Apache 2.0 | the project header only                               |
| upstream Mobius file, MIT      | MIT        | keep the upstream header, add the L2NX copyright line |
| upstream Mobius file, GPLv3    | GPLv3      | keep the upstream header, add the L2NX copyright line |

- Renaming packages or variables is not a rewrite.
- **Rewriting a GPLv3 file** to move it to Apache 2.0 starts from the behaviour, not from editing the GPLv3 text. This
  is how the build gets rid of GPLv3 ([#1](https://github.com/l2nx/nx-l2/issues/1)).
- `libs/` and `engine/` take no GPLv3 file.

## File headers

Every file carries an SPDX header ([REUSE](https://reuse.software)); CI runs `reuse lint`. Files that cannot hold a
comment are covered in [REUSE.toml](REUSE.toml).

<!-- REUSE-IgnoreStart -->

New file:

```java
/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
```

Modified upstream file: the upstream header stays as it is; add above it

```java
/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
```

with the identifier the upstream header states (`MIT` or `GPL-3.0-or-later`).

<!-- REUSE-IgnoreEnd -->

## Workflow

- **Design first.** A feature gets a spec in `docs/specs/NNN-<feature>.md` before implementation; the index is in
  [docs/CLAUDE.md](docs/CLAUDE.md).
- **Deferred work is a GitHub issue,** listed in one line in [docs/TODO.md](docs/TODO.md).
- **Commits** follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <subject>`.
- **Moves and renames** go through `git mv`, in a commit separate from content changes, so history follows the file.
- **Language:** English for code, comments and docs.
