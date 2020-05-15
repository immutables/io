const ver = {
	guava: '28.2-jre',
	guice: '4.2.2',
	gson: '2.8.6',
	asm: '8.0.1',
	immutables: '2.8.0-dt-experimental-2',
}

module.exports = function(up) { up
  .lib('//lib/javax/annotation', `com.google.code.findbugs:jsr305:3.0.1`)
	.lib('//lib/javax/inject', `javax.inject:javax.inject:1`)
	.lib('//lib/google/common', `com.google.guava:guava:${ver.guava}`)
	.lib('//lib/google/inject', `com.google.inject:guice:no_aop:${ver.guice}`, {
		srcs: `com.google.inject:guice:${ver.guice}`,
	})
	.lib('//lib/immutables/value:annotations', `org.immutables:value:annotations:${ver.immutables}`)
	.lib('//lib/immutables/value', `org.immutables:value:${ver.immutables}`, {
		srcs: [],
	})
	.lib('//lib/immutables/data', `org.immutables:data:${ver.immutables}`)
	.lib('//lib/immutables/value:processor', [], {
		processor: 'org.immutables.processor.ProxyProcessor',
		deps: ['//lib/immutables/value'],
	})
	.lib('//lib/google/gson', `com.google.code.gson:gson:${ver.gson}`)
	.lib('//lib/square/okio', `com.squareup.okio:okio:1.16.0`)
	.lib('//lib/square/moshi', `com.squareup.moshi:moshi:1.9.2`, {
		deps: ['//lib/square/okio']
	})
	.lib('//lib/immutables/trees', `org.immutables:trees:${ver.immutables}`)
	.lib('//lib/immutables/ordinal', `org.immutables:ordinal:${ver.immutables}`)
	.lib('//lib/immutables/generator', `org.immutables:generator:${ver.immutables}`)
	.lib('//lib/immutables/generator:processor', [
		`org.immutables:generator-processor:luggage:${ver.immutables}`,
		`org.ow2.asm:asm:${ver.asm}`,
		`org.ow2.asm:asm-tree:${ver.asm}`,
		`org.ow2.asm:asm-analysis:${ver.asm}`,
		`org.ow2.asm:asm-util:${ver.asm}`,
	], {
		deps: ['//lib/google/common', '//lib/immutables/trees'],
	})
  .lib('//lib/javapoet', `com.squareup:javapoet:1.12.1`)
	.lib('//lib/postresql', `org.postgresql:postgresql:42.2.12`)
	.lib('//lib/junit', [
		`junit:junit:4.12`,
		`org.hamcrest:hamcrest-core:1.3`,
	])
}
