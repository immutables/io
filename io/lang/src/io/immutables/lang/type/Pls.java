package io.immutables.lang.type;

import java.util.concurrent.Callable;

@Deprecated
public class Pls {

	static <T extends Runnable> T abc() {
		//t.run();
		class X implements Runnable {
			@Override public void run() {

			}
		};
		return (T) new X();
	}

	static <L extends Callable<L>> L bbz(L l)  {
		return null;
	}

	static <U extends Runnable & Callable<U>> void bbo() {
		U a = null;
		var x = bbz(abc());
	}

	public static void main(String[] args) {



	}
}
