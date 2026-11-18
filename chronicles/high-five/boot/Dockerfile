# SPDX-FileCopyrightText: 2026 L2NX contributors
# SPDX-License-Identifier: Apache-2.0
#
# Built from the installed distribution, with this directory as the context:
#   ./gradlew :chronicles:high-five:boot:installDist
#   docker build -t l2nx/game-server chronicles/high-five/boot
# The stop saves every online player and runs until the save is done, so the container needs a stop timeout well above the
# save time instead of the Docker default of 10 s (docker stop --time, Compose stop_grace_period).

FROM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6

RUN groupadd --system l2nx && useradd --system --gid l2nx --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin l2nx

WORKDIR /opt/game-server

COPY build/install/game-server/bin bin
COPY build/install/game-server/lib lib
# The server writes into config/ (hexid.txt) and data/ next to its datapack, and creates log/.
COPY --chown=l2nx:l2nx build/install/game-server/config config
COPY --chown=l2nx:l2nx build/install/game-server/data data
RUN install -d -o l2nx -g l2nx log data/geodata

USER l2nx

# The management port is bound to loopback by default; in the container it listens on all interfaces so that an explicitly
# published port reaches it. Nothing is published by default.
ENV SERVER_ADDRESS=0.0.0.0

VOLUME ["/opt/game-server/data/geodata"]

# 7777 game clients (GameserverPort in config/Server.ini), 8777 Actuator (server.port).
EXPOSE 7777 8777

# The image has neither curl nor wget, so the probe speaks HTTP over bash's /dev/tcp.
HEALTHCHECK --interval=15s --timeout=5s --start-period=5m --retries=3 \
	CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8777} && printf 'GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3 && read -r _ status _ <&3 && [ \"$status\" = 200 ]"]

ENTRYPOINT ["bin/game-server"]
