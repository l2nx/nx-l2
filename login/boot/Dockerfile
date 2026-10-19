# SPDX-FileCopyrightText: 2026 L2NX contributors
# SPDX-License-Identifier: Apache-2.0
#
# Built from the installed distribution, with this directory as the context:
#   ./gradlew :login:boot:installDist
#   docker build -t l2nx/login-server login/boot

FROM eclipse-temurin:25-jre

RUN groupadd --system l2nx && useradd --system --gid l2nx --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin l2nx

WORKDIR /opt/login-server

COPY build/install/login-server/bin bin
COPY build/install/login-server/lib lib
# The distribution stays read-only for the server user; it creates and writes only log/.
COPY build/install/login-server/data data
COPY build/install/login-server/banned_ip.cfg banned_ip.cfg
RUN install -d -o l2nx -g l2nx log

USER l2nx

# The management port is bound to loopback by default; in the container it listens on all interfaces so that an explicitly
# published port reaches it. Nothing is published by default.
ENV SERVER_ADDRESS=0.0.0.0

# 2106 login clients (l2nx.login.client.port), 9014 game servers (l2nx.login.game-server.port), 8106 Actuator (server.port).
EXPOSE 2106 9014 8106

# The image has neither curl nor wget, so the probe speaks HTTP over bash's /dev/tcp.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
	CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8106} && printf 'GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3 && read -r _ status _ <&3 && [ \"$status\" = 200 ]"]

ENTRYPOINT ["bin/login-server"]
