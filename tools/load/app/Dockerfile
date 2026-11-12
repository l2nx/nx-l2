# SPDX-FileCopyrightText: 2026 L2NX contributors
# SPDX-License-Identifier: Apache-2.0
#
# Built from the installed distribution, with this directory as the context:
#   ./gradlew :tools:load:app:installDist
#   docker build -t l2nx/load tools/load/app

FROM eclipse-temurin:25-jre@sha256:a640968ae55027b322cedd5f8228b01a84baff0b9e9360e47601c6a111ba1699

RUN groupadd --system l2nx && useradd --system --gid l2nx --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin l2nx

WORKDIR /opt/load

COPY build/install/load/bin bin
COPY build/install/load/lib lib
# The default l2nx.load.output-dir, relative to the working directory; a run mounts the host's directory here.
RUN install -d -o l2nx -g l2nx build/load

USER l2nx

ENTRYPOINT ["bin/load"]
