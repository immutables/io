package io.immutables.micro;

import java.security.SecureRandom;
import com.google.common.primitives.UnsignedLongs;
import org.immutables.data.Data;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

/**
 * Instance of exception token is created in exchange for consumed or unhandled exception sent to the sink.
 */
@Data
@Immutable
public abstract class ExceptionToken {
  @Parameter
  abstract long value();

  @Override
  public String toString() {
    return "!exception " + getValue();
  }

  public String getValue() {
    return UnsignedLongs.toString(value());
  }

  public static ExceptionToken of(long value) {
    return ImmutableExceptionToken.of(value);
  }

  public static ExceptionToken random() {
    return of(random.nextLong());
  }

  // secure random is thread safe and no much contention is expected so no need
  // for thread-local random generator.
  private static final SecureRandom random = new SecureRandom();
}
