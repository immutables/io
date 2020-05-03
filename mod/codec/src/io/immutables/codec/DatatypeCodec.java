package io.immutables.codec;

import java.io.IOException;

// TODO
public class DatatypeCodec<T> extends Codec<T> {
	private final Datatype<T> meta;
	private final Lookup lookup;

	public DatatypeCodec(Datatype<T> meta, Lookup lookup) {
		this.meta = meta;
		this.lookup = lookup;
		// TODO
	}

	@Override
	public T decode(In in) throws IOException {
		return null;
	}

	@Override
	public void encode(Out out, T instance) throws IOException {
		
	}
}
