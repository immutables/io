package io.immutables.ecs.generate;

import org.immutables.generator.Builtins;
import org.immutables.generator.Generator;
import org.immutables.generator.Templates;

@Generator.Template
public abstract class Ecs extends Builtins {
	public final Output output = new Output();
	
	abstract Templates.Invokable generate();
}
