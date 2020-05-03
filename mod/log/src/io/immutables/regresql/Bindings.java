package io.immutables.regresql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public abstract class Bindings {
	private Bindings() {}
	
	interface Slot {
		String name();
	}
	
	/**
	 * This mapping is how we write and read values to JDBC, not the actual SQL type mappings.
	 * For example, CHAR, VARCHAR, TEXT, JSON all can be set using
	 * string value using JDBC. We cannot and don't have to bother convert to
	 * distinct SQL types in this case.
	 */
	enum MappingType {
		BOOL,
		INT,
		LONG,
		STRING,
		OBJECT;
		
		void set(PreparedStatement statement, int position, Object value) {
			
		}
		
		Object get(ResultSet results, int position) {
			return position;
		}
	}

	public static void main(String... args) {

	}
}
