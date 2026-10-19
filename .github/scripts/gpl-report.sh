#!/bin/sh
set -e
files=$(git grep -l 'SPDX-License-Identifier[:] GPL-3.0-or-later' -- ':!.github' ':!*.md' || true)
if [ -z "${files}" ]; then
	echo "No GPLv3 files."
	exit 0
fi
echo "GPLv3 files per module:"
echo "${files}" | sed -E 's#/src/.*##' | sort | uniq -c
forbidden=$(echo "${files}" | grep -E '^(libs|engine)/' || true)
if [ -n "${forbidden}" ]; then
	echo "GPLv3 files are not allowed in libs/ and engine/:"
	echo "${forbidden}"
	exit 1
fi
