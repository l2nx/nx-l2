# SPDX-FileCopyrightText: 2026 L2NX contributors
# SPDX-License-Identifier: Apache-2.0
#
# Built from the installed distribution, with this directory as the context:
#   ./gradlew :tools:load:app:installDist
#   docker build -t l2nx/load tools/load/app

FROM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6

RUN groupadd --system l2nx && useradd --system --gid l2nx --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin l2nx

WORKDIR /opt/load

COPY build/install/load/bin bin
COPY build/install/load/lib lib
# The default l2nx.load.output-dir, relative to the working directory; a run mounts the host's directory here.
RUN install -d -o l2nx -g l2nx build/load

USER l2nx

ENTRYPOINT ["bin/load"]
