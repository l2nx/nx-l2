# SPDX-FileCopyrightText: 2026 L2NX contributors
# SPDX-License-Identifier: Apache-2.0
#
# Rewrites the L2J Mobius history into the nx-l2 layout (docs/provenance.md).
# Run in a fresh clone of upstream:
#   git filter-repo --refs refs/heads/master --path L2J_Mobius_HighFive/ --path L2J_Mobius_CT_2.6_HighFive/ \
#     --filename-callback "$(cat filter-repo.py)"
# The body is the callback: `filename` is bytes; return the new path, or None to drop the file.
# An unmapped path raises, so an upstream layout change fails the import.

import re

CHRONICLE_ROOTS = (b"L2J_Mobius_HighFive/", b"L2J_Mobius_CT_2.6_HighFive/")

STRIPPED = re.compile(
    rb"(.*\.(jar|exe)|\.classpath|\.project|\.settings/.*|launcher/.*|build\.xml|\.gitignore|\.gitattributes)$"
)

HF_SERVER = b"chronicles/high-five/server/src/main/java/app/l2nx/highfive/"
HF_SCRIPTS = b"chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/"
HF_DATAPACK = b"chronicles/high-five/datapack/"
HF_DIST = b"chronicles/high-five/boot/src/dist/"
LOGIN_SERVER = b"login/server/"
LOGIN_JAVA = b"login/server/src/main/java/app/l2nx/login/"
LOGIN_DIST = b"login/boot/src/dist/"
COMMONS = b"libs/commons/src/main/java/app/l2nx/commons/"

GAME_LOG_FORMATTERS = re.compile(rb"log/formatter/(AccountingFormatter|EnchantFormatter|ItemLogFormatter)\.java$")

# Ordered: the first matching prefix wins.
JAVA_RULES = (
    (b"gameserver/", HF_SERVER),
    (b"loginserver/", LOGIN_JAVA),
    (b"tools/", LOGIN_JAVA + b"tools/"),
    (b"commons/", COMMONS),
    (b"log/", COMMONS + b"log/"),
    # Upstream's root util package was moved into commons; mapping it there keeps one history per file.
    (b"util/", COMMONS + b"util/"),
    (b"", HF_SERVER),
)

DIST_RULES = (
    (b"game/config/", HF_DATAPACK + b"config/"),
    (b"game/data/", HF_DATAPACK + b"data/"),
    (b"db_installer/sql/game/", HF_DATAPACK + b"sql/"),
    (b"db_installer/sql/login/", LOGIN_SERVER + b"sql/"),
    (b"login/", LOGIN_DIST),
    (b"game/", HF_DIST),
    (b"images/", HF_DIST + b"images/"),
    (b"db_installer/", HF_DIST + b"db_installer/"),
    (b"backup/", HF_DIST + b"backup/"),
)


def map_path(path):
    # A `deleteall` change carries no path.
    if path is None:
        return None
    rel = None
    for root in CHRONICLE_ROOTS:
        if path.startswith(root):
            rel = path[len(root) :]
            break
    if rel is None or STRIPPED.match(rel):
        return None

    java = re.match(rb"java/(?:com|org)/l2jmobius/(.*)$", rel)
    if java:
        sub = java.group(1)
        if GAME_LOG_FORMATTERS.match(sub):
            return HF_SERVER + sub
        for prefix, target in JAVA_RULES:
            if sub.startswith(prefix):
                return target + sub[len(prefix) :]

    scripts = re.match(rb"dist/game/data/scripts/(.*\.java)$", rel)
    if scripts:
        return HF_SCRIPTS + scripts.group(1)

    if rel.startswith(b"dist/"):
        sub = rel[len(b"dist/") :]
        for prefix, target in DIST_RULES:
            if sub.startswith(prefix):
                return target + sub[len(prefix) :]

    if rel == b"readme.txt":
        return HF_DIST + rel

    raise ValueError("unmapped upstream path: " + path.decode("utf-8", "replace"))


return map_path(filename)
