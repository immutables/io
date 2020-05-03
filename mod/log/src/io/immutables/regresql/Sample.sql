--.method2

--.method1

select * from a where a.a = :a1.k2 and b.b = :b or a.b::jsonb is null

--

type Ab {
	a Int    -> a_a int
	b String -> b_b varchar
	c	Cuc		 -> c_c json
}

Cuc {
	s Int
	u String
}

column mapping

a -> a_a
b -> b_b
c -> c_c "{s:, u:, b:}"::jsonb

