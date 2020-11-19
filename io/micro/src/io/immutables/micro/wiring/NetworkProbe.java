package io.immutables.micro.wiring;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

// Move this together with docker stuff to a separate module
public class NetworkProbe {

  private static final int CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(1);

  public static boolean isListening(InetSocketAddress address) {
    try (Socket probe = new Socket()) {
      // short enough but no nonsense timeout (as for localhost)
      probe.connect(address, CONNECT_TIMEOUT_MILLIS);
      return true;
    } catch (IOException cannotConnect) {
      // we care about success more that printing 'cannot connect' or 'timeout' exception.
      return false;
    }
  }

  public static boolean isListening(String host, int port) {
    return isListening(new InetSocketAddress(host, port));
  }
}
