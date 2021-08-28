package io.immutables.micro.kafka;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

class RuntimeInfo {
  static final long pid;
  static final String at;
  static {
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    pid = runtime.getPid();
    at = runtime.getName().replaceAll("^[0-9]+@", "");
  }
  static String key() {
    return pid + "@" + at;
  }
}
