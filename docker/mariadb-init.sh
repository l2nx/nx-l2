#!/bin/sh
set -e
for file in /sql/login/*.sql /sql/game/*.sql; do
	echo "nx-l2: applying ${file}"
	mariadb -uroot "${MARIADB_DATABASE}" < "${file}"
done
