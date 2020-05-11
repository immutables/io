--.method1

drop table if exists bu;
create table bu(a int, b text, c jsonb);
insert into bu(a, b, c) values (1, 'A', '[1]'::jsonb);
insert into bu(a, b, c) values (2, 'B', '[2]'::jsonb);
insert into bu(a, b, c) values (3, 'C', '[3]'::jsonb);

--.method2

select * from bu where a = 3;

--select * from a where a.a = :a1.k2 and b.b = :b or a.b::jsonb is null

