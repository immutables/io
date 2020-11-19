package io.immutables.micro.wiring;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import static java.net.InetAddress.getLoopbackAddress;

/**
 * Finding free ports, finding out if ports are listening.
 */
public final class LocalPorts {
  private LocalPorts() {}

  public static boolean isListening(int port) {
    return NetworkProbe.isListening(new InetSocketAddress(getLoopbackAddress(), port));
  }

  /**
   * Finds some port to launch server, which has high probability to be open. Uses {@code new ServerSocket(0)} to launch
   * server socket on some free port, grab port's number and closes the socket immediately. Of course, someone else
   * might snatch that port after it was discovered but before caller launch it's server on that port.
   */
  public static int findSomeFreePort() {
    try (ServerSocket s = new ServerSocket(PORT_AUTO_ASSIGNED)) {
      return s.getLocalPort();
    } catch (IOException ex) {
      return PORT_UNAVAILABLE;
    }
  }

  /**
   * Checks if it's "localhost" or IPv4 loopback address, for us it's like the same but, obvously, this is a
   * simplification.
   */
  public static boolean isLocalhost(String host) {
    return host.equals("localhost")
        || host.equals("127.0.0.1");
  }

  private static final int PORT_AUTO_ASSIGNED = 0;
  private static final int PORT_UNAVAILABLE = -1;
}
