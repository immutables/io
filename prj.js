let l = require('./.prj.lib')

l.project({
	lib: {
		guava: 'com.google.guava:guava:20.0',
		jsr305: 'com.google.code.findbugs:jsr305:3.0.1',
		junit: 'junit:junit:4.12',
		hamcrest: 'org.hamcrest:hamcrest-core:1.3',
		immutables: 'org.immutables:value:2.5.2',
		immutables_trees: 'org.immutables:trees:2.5.2',
		immutables_ordinal: 'org.immutables:ordinal:2.5.2',
		immutables_annotations: 'org.immutables:value:2.5.2:annotations',
		immutables_generator: 'org.immutables:generator-processor:2.5.2:luggage',
		// immutables_gson: 'org.immutables:gson:2.5.2',
		// gson: 'com.google.code.gson:gson:2.8.0',
		// jackson: 'com.fasterxml.jackson.core:jackson-core:2.8.5',
		// jackson_yaml: 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.8.5',
		// retrofit: 'com.squareup.retrofit2:retrofit:2.1.0',
		// retrofit_gson: 'com.squareup.retrofit2:converter-gson:2.1.0',
		// okhttp: 'com.squareup.okhttp3:okhttp:3.3.0',
		// okio: 'com.squareup.okio:okio:1.11.0',
		// snake_yaml: 'org.yaml:snakeyaml:1.15',
	},
	deps: {
		junit: ['hamcrest'],
		immutables_generator: ['guava'],
		// jackson_yaml: ['snake_yaml'],
		// okhttp: ['okio'],
		// retrofit: ['retrofit_gson', 'okhttp'],
		//retrofit_gson: ['gson'],
		//immutables_gson: ['gson'],
	},
	src: [
		'src',
	],
	gen: [
		'.gen/src',
		l.gensrc('//src/io/immutables/grammar/fixture:fixture'),
		l.gensrc('//src/io/immutables/lang:lang'),
		l.gensrc('//src/io/immutables/lang/fixture:fixture'),
	],
	classpath_exclude: {
		immutables: true
	},
	eclipse_apt: [
		'//src:metainf_extensions_jar',
		'//lib:immutables',
	]
})
