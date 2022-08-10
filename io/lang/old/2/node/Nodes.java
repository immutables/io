package io.immutables.lang.node;

import java.util.Objects;
import java.util.function.Consumer;

class Nodes {

	static Node.ApplyFeature access(String name) {
		var a = new Node.ApplyFeature();
		a.name = name;
		return a;
	}

	static Node.ApplyFeature access(Node on, String name) {
		var a = new Node.ApplyFeature();
		a.name = name;
		a.on = on;
		return a;
	}

	static Node.ApplyFeature apply(Node on, String name, Node in) {
		var a = new Node.ApplyFeature();
		a.name = name;
		a.on = on;
		a.in = in;
		return a;
	}

	static Node.ApplyFeature plus(Node a, Node b) {
		return apply(a, "+", b);
	}

	static Node.ConstructProduct empty() {
		var p = new Node.ConstructProduct();
		p.components = new Node[0];
		return p;
	}

	static Node.ConstructProduct product(Node c0, Node c1, Node... cs) {
		var p = new Node.ConstructProduct();
		p.components = new Node[2 + cs.length];
		p.components[0] = c0;
		p.components[1] = c1;
		System.arraycopy(cs, 0, p.components, 2, cs.length);
		return p;
	}

	static Node.IntLiteral number(int value) {
		var l = new Node.IntLiteral();
		l.value = value;
		return l;
	}

	static Node.StrLiteral str(String value) {
		var l = new Node.StrLiteral();
		l.value = value;
		return l;
	}

	static String showFeature(Node.ApplyFeature a) {
		if (a.name.equals("+")) return a.on + " + " + a.in;

		var sb = new StringBuilder();
		if (a.on != null) {
			sb.append(a.on).append(".");
		} else {
			sb.append("$");
		}
		sb.append(a.name);
		if (a.in != null) {
			if (a.in instanceof Node.ConstructProduct) {
				sb.append(a.in);
			} else {
				sb.append("(").append(a.in).append(")");
			}
		}
		return sb.toString();
	}

	static boolean ifApplyFeature(Node node, Consumer<Node.ApplyFeature> f) {
		if (node instanceof Node.ApplyFeature) {
			f.accept((Node.ApplyFeature) node);
			return true;
		}
		return false;
	}

	static boolean ifIntLiteral(Node node, Consumer<Node.IntLiteral> f) {
		if (node instanceof Node.IntLiteral) {
			f.accept((Node.IntLiteral) node);
			return true;
		}
		return false;
	}

	static boolean ifStrLiteral(Node node, Consumer<Node.StrLiteral> f) {
		if (node instanceof Node.StrLiteral) {
			f.accept((Node.StrLiteral) node);
			return true;
		}
		return false;
	}

	static boolean ifConstructProduct(Node node, Consumer<Node.ConstructProduct> f) {
		if (node instanceof Node.ConstructProduct) {
			f.accept((Node.ConstructProduct) node);
			return true;
		}
		return false;
	}

	static void printTyped(Node node) {
		node.traverse(n -> {}, n -> {
			System.out.println("T| " + n + "\t\t\t\t\t>>  " + Objects.toString(n.type, "_"));
		});
		//System.out.println("F| " + feature);
	}
}
