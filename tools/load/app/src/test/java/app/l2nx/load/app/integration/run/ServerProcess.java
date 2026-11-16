/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.integration.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A server started from its distribution, as the deployment runs it: the working directory holds config and data. */
final class ServerProcess implements AutoCloseable {
    private static final Duration POLL = Duration.ofMillis(250);
    private static final int LOG_TAIL_LINES = 40;

    private final String name;
    private final Process process;
    private final Path log;

    private ServerProcess(String name, Process process, Path log) {
        this.name = name;
        this.process = process;
        this.log = log;
    }

    static ServerProcess start(String name, Path distribution, String mainClass, Path log, List<String> arguments)
            throws IOException {
        final String java = ProcessHandle.current().info().command().orElseThrow();
        final List<String> command = new ArrayList<>(List.of(
                java,
                "-Dfile.encoding=UTF-8",
                "-Djava.awt.headless=true",
                "-XX:+ExitOnOutOfMemoryError",
                "-Xmx2g",
                "-cp",
                distribution.resolve("lib").toAbsolutePath() + "/*",
                mainClass));
        command.addAll(arguments);
        final Process process = new ProcessBuilder(command)
                .directory(distribution.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        return new ServerProcess(name, process, log);
    }

    String awaitLog(Pattern line, Duration timeout) throws IOException, InterruptedException {
        final Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            final Matcher matcher = line.matcher(Files.readString(log, StandardCharsets.ISO_8859_1));
            if (matcher.find()) {
                return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
            }
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        name + " exited with " + process.exitValue() + " before logging " + line + ", " + logTail());
            }
            TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
        }
        throw new IllegalStateException(name + " did not log " + line + " in " + timeout + ", " + logTail());
    }

    /** The log of a CI runner is gone with its workspace, so a failure carries the end of it. */
    private String logTail() throws IOException {
        final List<String> lines = Files.readAllLines(log, StandardCharsets.ISO_8859_1);
        return "the last lines of " + log + ":\n"
                + String.join("\n", lines.subList(Math.max(0, lines.size() - LOG_TAIL_LINES), lines.size()));
    }

    /** Forcibly: every load bot has logged out by now, and nothing the servers still hold matters to the test. */
    @Override
    public void close() throws InterruptedException {
        process.destroyForcibly();
        process.waitFor();
    }
}
