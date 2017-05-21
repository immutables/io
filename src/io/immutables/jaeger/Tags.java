package io.immutables.jaeger;

import com.google.common.collect.Maps;
import java.util.Map.Entry;
import java.util.Optional;

enum Tag {
	// specify json-schema type
	TYPE,
	// specify json-schema format
	FORMAT,
	// the shortened description
	SUMMARY,
	// specify name which is different than the identifier
	NAME;

	private final String prefix = name().toLowerCase() + ": ";

	static Optional<Entry<Tag, String>> detect(CharSequence comment) {
		String s = comment.toString().trim();
		for (Tag t : values()) {
			if (s.startsWith(t.prefix)) {
				return Optional.of(
						Maps.immutableEntry(t, s.substring(t.prefix.length())));
			}
		}

		return Optional.empty();
	}
}
