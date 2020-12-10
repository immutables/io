package io.immutables.ecs.gen;

import io.immutables.ecs.def.Definition;
import io.immutables.ecs.def.Model;
import org.immutables.generator.Builtins;
import org.immutables.generator.Generator;
import org.immutables.generator.Templates;

@Generator.Template
abstract class Schema extends Builtins {
	Output output;
	Model model;

	abstract Templates.Invokable generate();

	@Generator.Typedef io.immutables.ecs.def.Definition Definition;
	@Generator.Typedef io.immutables.ecs.def.Definition.Module Module;
	@Generator.Typedef Definition.NamedParameter Parameter;
	@Generator.Typedef Definition.Constructor Constructor;
	@Generator.Typedef Definition.DataTypeDefinition Datatype;
	@Generator.Typedef io.immutables.ecs.def.Definition.ContractDefinition Contract;
	@Generator.Typedef Model.Component Component;
	@Generator.Typedef Model.DataType DataTypeModel;
	@Generator.Typedef Model.Contract ContractModel;
}
