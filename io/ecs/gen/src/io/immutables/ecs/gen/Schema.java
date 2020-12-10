package io.immutables.ecs.gen;

import io.immutables.collect.Vect;
import io.immutables.ecs.def.Definition;
import io.immutables.ecs.def.Model;
import io.immutables.ecs.def.Type;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
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
	@Generator.Typedef Definition.Module ModuleDefinition;
	@Generator.Typedef Definition.EntityDefinition EntityDefinition;
	@Generator.Typedef io.immutables.ecs.def.Definition.ContractDefinition Contract;
	@Generator.Typedef Model.Component Component;
	@Generator.Typedef Model.DataType DataTypeModel;
	@Generator.Typedef Model.Contract ContractModel;
	@Generator.Typedef TypeDecider Typed;

	final Predicate<Definition.TypeSignature> isInline = t -> t.hasConcept(Compiler.systemInline);
	final Predicate<Type> isRequired = t -> new TypeDecider(t).isRequired();

	final Function<Type, TypeDecider> decideType = TypeDecider::new;
	final Function<String, Iterable<String>> linesOf = commentBlock -> {
		var lines = Vect.from(Splitter.on('\n').split(commentBlock));
		if (lines.all(l -> l.isEmpty() || l.startsWith(" "))) {
			lines = lines.map(l -> l.isEmpty()  ? l : l.substring(" ".length()));
		}
		return lines;
	};

	final Function<Type, Definition.DataTypeDefinition> findDataType = t -> {
		var decider = new TypeDecider(t);
		var dt = decider.getElement().type.ifReference(r -> model.getDatatype(r.module(), r.name()), null);
		return dt != null ? dt.definition() : null;
	};

	final class TypeDecider {
		final Type type;

		TypeDecider(Type type) {
			this.type = type;
		}

		boolean isArray() {
			return type instanceof Type.Array;
		}

		boolean isSetn() {
			return type instanceof Type.Setn;
		}

		boolean isMapn() {
			return type instanceof Type.Mapn;
		}

		boolean isOption() {
			return type instanceof Type.Option;
		}

		boolean isArrayLike() {
			return isArray() || isSetn();
		}

		boolean isRequired() {
			return !isOption() && !isArrayLike() && !isMapn();
		}

		boolean isReferenceLike() {
			return isReference() || isParameterized();
		}

		boolean isReference() {
			return type instanceof Type.Reference;
		}

		boolean isParameterized() {
			return type instanceof Type.Parameterized;
		}

		boolean isSystem() {
			return type instanceof Type.Reference
					&& ((Type.Reference) type).module().equals("system");
		}

		TypeDecider getUnparameterized() {
			if (type instanceof Type.Parameterized) {
				return new TypeDecider(((Type.Parameterized) type).reference());
			}
			return this;
		}

		String getName() {
			if (type instanceof Type.Reference) {
				return ((Type.Reference) type).name();
			}
			if (type instanceof Type.Parameterized) {
				return ((Type.Parameterized) type).reference().name();
			}
			return type.toString();
		}

		String getModule() {
			if (type instanceof Type.Reference) {
				return ((Type.Reference) type).module();
			}
			if (type instanceof Type.Parameterized) {
				return ((Type.Parameterized) type).reference().module();
			}
			return "";
		}

		TypeDecider getElement() {
			return new TypeDecider(type.accept(new Type.Visitor<Void, Type>() {
				@Override
				public Type setn(Type.Setn s, Void in) {
					return s.component();
				}
				@Override
				public Type array(Type.Array a, Void in) {
					return a.component();
				}
				@Override
				public Type option(Type.Option o, Void in) {
					return o.component();
				}

				@Override
				public Type reference(Type.Reference d, Void in) {
					return d;
				}

				@Override
				public Type parameterized(Type.Parameterized d, Void in) {
					return d.reference();
				}

				@Override
				public Type otherwise(Type t, Void in) {
					return t;
				}
			}, null));
		}

		TypeDecider getKey() {
			return new TypeDecider(type.accept(new Type.Visitor<Void, Type>() {
				@Override
				public Type mapn(Type.Mapn m, Void in) {
					return m.key();
				}
				@Override
				public Type otherwise(Type t, Void in) {
					return Type.Empty.of();
				}
			}, null));
		}

		TypeDecider getValue() {
			return new TypeDecider(type.accept(new Type.Visitor<Void, Type>() {
				@Override
				public Type mapn(Type.Mapn m, Void in) {
					return m.value();
				}
				@Override
				public Type otherwise(Type t, Void in) {
					return Type.Empty.of();
				}
			}, null));
		}

		Vect<TypeDecider> getArguments() {
			return type.accept(new Type.Visitor<Void, Vect<Type>>() {
				@Override
				public Vect<Type> setn(Type.Setn s, Void in) {
					return Vect.of(s.component());
				}
				@Override
				public Vect<Type> array(Type.Array a, Void in) {
					return Vect.of(a.component());
				}
				@Override
				public Vect<Type> option(Type.Option o, Void in) {
					return Vect.of(o.component());
				}
				@Override
				public Vect<Type> mapn(Type.Mapn m, Void in) {
					return Vect.of(m.key(), m.value());
				}
				@Override
				public Vect<Type> parameterized(Type.Parameterized d, Void in) {
					return d.arguments();
				}
				@Override
				public Vect<Type> otherwise(Type t, Void in) {
					return Vect.of();
				}
			}, null).map(TypeDecider::new);
		}
	}
}
