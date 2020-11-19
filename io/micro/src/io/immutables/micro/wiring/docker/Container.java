package io.immutables.micro.wiring.docker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.immutables.value.Value;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

@Deprecated
    // TODO need to revise / refactor this crap
interface Container {

  String name(Context context);

  String serviceName();

  String image();

  String cmd(Context context);

  List<ExposedPort> exposedPorts(Context context);

  List<EnvironmentVariable> envVariables(Context context);

  List<String> network();

  class Builder {
    private Function<Context, String> name;
    private String serviceName;
    private String imageName;
    private Function<Context, String> cmd = context -> "";
    private List<Function<Context, ExposedPort>> exposedPorts = new ArrayList<>();
    private List<Function<Context, EnvironmentVariable>> environmentVariables = new ArrayList<>();
    private List<String> networks = new ArrayList<>();

    public Container.Builder name(Function<Context, String> name) {
      this.name = name;
      return this;
    }

    public Container.Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public Container.Builder image(String imageName) {
      this.imageName = imageName;
      return this;
    }

    public Container.Builder cmd(Function<Context, String> cmd) {
      this.cmd = cmd;
      return this;
    }

    public Container.Builder addExposedPort(Function<Context, ExposedPort> exposedPort) {
      this.exposedPorts.add(exposedPort);
      return this;
    }

    public Container.Builder addEnvironmentVariable(Function<Context, EnvironmentVariable> variable) {
      this.environmentVariables.add(variable);
      return this;
    }

    public Container.Builder addToNetwork(String network) {
      this.networks.add(network);
      return this;
    }

    public Container build() {
      return new Container() {
        @Override
        public String name(Context context) {
          return name.apply(context);
        }

        @Override
        public String serviceName() {
          return serviceName;
        }

        @Override
        public String image() {
          return imageName;
        }

        @Override
        public String cmd(Context context) {
          return cmd.apply(context);
        }

        @Override
        public List<ExposedPort> exposedPorts(Context context) {
          return exposedPorts.stream().map(port -> port.apply(context)).collect(toList());
        }

        @Override
        public List<EnvironmentVariable> envVariables(Context context) {
          return environmentVariables.stream().map(variable -> variable.apply(context)).collect(toList());
        }

        @Override
        public List<String> network() {
          return unmodifiableList(networks);
        }
      };
    }
  }
}

@Value.Immutable
interface Context {

  @Value.Parameter
  Integer externalPort();

  @Value.Parameter
  Supplier<Integer> randomPortSupplier();

  @Value.Parameter
  Function<String, String> serviceHostnameResolver();

  @Value.Parameter
  boolean isLocalhostAvailable();
}

@Value.Immutable
interface EnvironmentVariable {

  @Value.Parameter
  String name();

  @Value.Parameter
  String value();

  static EnvironmentVariable of(String name, String value) {
    return ImmutableEnvironmentVariable.of(name, value);
  }
}

@Value.Immutable
interface ExposedPort {
  @Value.Parameter
  int external();

  @Value.Parameter
  int internal();

  static ExposedPort of(int external, int internal) {
    return ImmutableExposedPort.of(external, internal);
  }
}

