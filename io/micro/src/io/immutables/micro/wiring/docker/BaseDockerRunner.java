package io.immutables.micro.wiring.docker;

import io.immutables.micro.wiring.LocalPorts;
import io.immutables.micro.wiring.NetworkProbe;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import static java.util.Collections.singleton;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;

abstract class BaseDockerRunner {

  private static final String BUILD_NETWORK_NAME = "build";

  abstract boolean isRunning(Container container, int port);

  abstract String getServiceHostname(String serviceName);

  abstract int getRandomPort();

  abstract boolean isLocalhostAvailable();

  void stop(String serviceName) {
    try {
      execute(String.format("%s kill %s", dockerCmd(), getContainerHost(serviceName)));
    } catch (IOException | InterruptedException e) {
      throw new AssertionError("Can't stop " + serviceName, e);
    }
  }

  void assertRunning(Container container, int port) {
    if (isRunning(container, port)) return;
    try {
      Thread.sleep(SECONDS.toMillis(1));

      String containerId = execute(toCmd(container, ImmutableContext.of(port, this::getRandomPort,
          this::getContainerHost, isLocalhostAvailable())));
      Iterables.concat(container.network(), singleton(BUILD_NETWORK_NAME))
          .forEach(network -> connectToNetwork(network, containerId));

      for (int i = 1; i < 10; i++) {
        Thread.sleep(SECONDS.toMillis(i));
        if (isRunning(container, port)) {
          report(container.serviceName() + " is now listening on " + getContainerHost(container.serviceName()) + ":" + port);
          return;
        }
      }
    } catch (InterruptedException | IOException e) {
      throw new AssertionError("Cannot get " + container.serviceName() + " running on " + getContainerHost(container.serviceName()) + ":" + port, e);
    }
    throw new AssertionError("Cannot get " + container.serviceName() + " running on " + getContainerHost(container.serviceName()) + ":" + port);
  }

  private void connectToNetwork(String networkName, String containerId) {
    try {
      String networkId = execute(dockerCmd() + " network inspect -f {{.Id}} " + networkName);
      if (networkId.trim().isEmpty()) {
        execute(dockerCmd() + " network create " + networkName);
      }
      execute(dockerCmd() + " network connect " + networkName + " " + containerId);
    } catch (IOException | InterruptedException e) {
      throw new AssertionError("Can't connect to network " + networkName, e);
    }
  }

  protected String execute(String command) throws IOException, InterruptedException {
    report("> %s", command);

    Process process = Runtime.getRuntime().exec(command);
    int exitCode = process.waitFor();

    report("$? = %s", exitCode);
    try (
        Reader err = new InputStreamReader(process.getErrorStream());
        Reader out = new InputStreamReader(process.getInputStream())) {
      report(CharStreams.toString(err));
      String commandOutput = CharStreams.toString(out);
      report(commandOutput);
      return Strings.isNullOrEmpty(commandOutput) ? "" : commandOutput.trim();
    }
  }

  private static void report(String message, Object... arguments) {
    System.err.format(message, arguments);
    System.err.println();
  }

  private String toCmd(Container container, Context context) {
    return dockerCmd() + " run -d --rm" +
        " --name " + container.name(context)
        + container.exposedPorts(context).stream().map(port -> "-p " + port.external() + ":" + port.internal()).collect(joining(" ", " ", " "))
        + container.envVariables(context).stream().map(variable -> "-e " + variable.name() + "=" + variable.value()).collect(Collectors.joining(" ", " ", " "))
        + " -l test-service=" + container.serviceName() + " "
        + container.image()
        + " " + container.cmd(context);
  }

  private String dockerCmd() {
    @Nullable String value = System.getenv("DOCKER_CMD");
    if (value != null) return value;
    return Stream.of("/usr/local/bin/docker", "/usr/bin/docker")
        .filter(f -> new File(f).canExecute())
        .findFirst()
        .orElse("docker");
  }

  String getContainerHost(String serviceName) {
    try {
      return execute(dockerCmd() + " ps --format {{.Names}} --filter label=test-service=" + serviceName).trim();
    } catch (IOException | InterruptedException e) {
      throw new AssertionError("Can't find hostname of the service " + serviceName);
    }
  }
}

class LocalDockerRunner extends BaseDockerRunner {

  @Override
  boolean isRunning(Container container, int port) {
    return LocalPorts.isListening(port);
  }

  @Override
  int getRandomPort() {
    return LocalPorts.findSomeFreePort();
  }

  @Override
  String getServiceHostname(String serviceName) {
    return "localhost";
  }

  @Override
  boolean isLocalhostAvailable() {
    return true;
  }
}

class ContainerDockerRunner extends BaseDockerRunner {

  private static final AtomicInteger PORT_SEQUENCE = new AtomicInteger(40000);

  @Override
  boolean isRunning(Container container, int port) {
    return NetworkProbe.isListening(getContainerHost(container.serviceName()), port);
  }

  @Override
  int getRandomPort() {
    return PORT_SEQUENCE.incrementAndGet();
  }

  @Override
  String getServiceHostname(String serviceName) {
    return getContainerHost(serviceName);
  }

  @Override
  boolean isLocalhostAvailable() {
    return false;
  }
}
