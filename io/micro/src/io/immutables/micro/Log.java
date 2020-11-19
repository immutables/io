package io.immutables.micro;

import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @deprecated This was just a stub. Correct logging abstraction is to come (please, this is not slf4j, that is a
 *     dead-end).
 */
@Deprecated
public class Log {

  static {
    try {
      LogManager.getLogManager().readConfiguration(Log.class.getResourceAsStream("/logging.properties"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  public static void log(String text) {
    Logger.getGlobal().info(text);
  }
}
