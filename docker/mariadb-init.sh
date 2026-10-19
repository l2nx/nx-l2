#!/bin/sh
set -e
for file in /sql/login/*.sql /sql/game/*.sql; do
	echo "nx-l2: applying ${file}"
	MYSQL_PWD="${MARIADB_PASSWORD}" mariadb -u"${MARIADB_USER}" "${MARIADB_DATABASE}" < "${file}"
done
