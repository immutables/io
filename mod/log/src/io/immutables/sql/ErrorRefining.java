package io.immutables.sql;

import io.immutables.Nullable;
import io.immutables.Source;
import io.immutables.collect.Vect;
import io.immutables.sql.Sqls.MethodDefinition;
import io.immutables.sql.Sqls.SqlSource;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;

class ErrorRefining {
	private ErrorRefining() {}
	private static final boolean postgresqlPresent;
	static {
		boolean isPresent = false;
		try {
			// trigger classloading
			PSQLException.class.getCanonicalName();
			isPresent = true;
		} catch (NoClassDefFoundError postgressDriverNotAvailable) {}

		postgresqlPresent = isPresent;
	}

	static SQLException refineException(
			SqlSource source,
			Method method,
			MethodDefinition definition,
			SQLException originalException) {

		if (!postgresqlPresent || !(originalException instanceof PSQLException)) {
			return originalException;
		}

		PSQLException ex = (PSQLException) originalException;
		ServerErrorMessage errorMessage = ex.getServerErrorMessage();
		// correction for 1-based position, which can be 0 if unknown
		int position = Math.max(0, errorMessage.getPosition() - 1);
		Source.Range range =
				Source.Range.of(source.get(definition.statementsRange().begin.position + position));

		String state = errorMessage.getSQLState();
		String stateInfo = Stream.of(PSQLState.values())
				.filter(e -> e.getState().equals(state))
				.map(Object::toString)
				.findFirst()
				.orElse("")
				.replace("_", " ")
				.toLowerCase();

		Source.Problem problem = source.problemAt(
				range,
				errorMessage.getMessage(),
				errorMessage.getSeverity() + " " + state + " " + stateInfo);

		SQLException exception =
				new SQLException(errorMessage.getMessage() + "\n" + problem, ex.getSQLState(), ex.getErrorCode());
		@Nullable SQLException nextException = ex.getNextException();
		if (nextException != null) {
			exception.setNextException(nextException);
		}
		exception.setStackTrace(trimStackTrace(exception.getStackTrace(), method));
		return exception;
	}

	private static StackTraceElement[] trimStackTrace(StackTraceElement[] stackTrace, Method method) {
		return Vect.of(stackTrace)
				.dropWhile(s -> !s.getClassName().contains(".$Proxy"))
				.rangeFrom(1)
				.prepend(new StackTraceElement(
						method.getDeclaringClass().getName(),
						method.getName(),
						"Dynamic Proxy",
						-1))
				.toArray(StackTraceElement[]::new);
	}
}
