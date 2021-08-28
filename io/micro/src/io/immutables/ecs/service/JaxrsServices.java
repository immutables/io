package io.immutables.ecs.service;

import java.util.LinkedHashSet;
import java.util.Set;
import com.google.common.base.Splitter;
import org.immutables.data.Data;
import org.immutables.value.Value;

/**
 * Placeholder for ugliness
 */
public class JaxrsServices {
	private JaxrsServices() {}

  public static Set<String> readSet(String value) {
    return new LinkedHashSet<>(COMMA.splitToList(value)); // maintain order
  }

  private static final Splitter COMMA = Splitter.on(',').omitEmptyStrings().trimResults();
}
