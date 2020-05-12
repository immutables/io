package io.immutables.grammar.processor;

import com.google.common.base.Joiner;
import java.util.function.Consumer;

final class Debug {
	private static Consumer<String> consumer;
	private static boolean logged;

	static void setConsumer(Consumer<String> consumer) {
		Debug.consumer = consumer;
	}

	static void log(Object... parts) {
		consumer.accept(Joiner.on(" ").join(parts));
		logged = true;
	}

	static boolean logged() {
		return logged;
	}
}
