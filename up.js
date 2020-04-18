// See https://github.com/immutables/highlands/blob/master/README.md
// In order to download required scripts and run full regeneration use `make up`
// after required scripts downloaded, you can use `node up --lib --intellij`
// or other options (see readme link)

const ver = {
	guava: '28.2-jre',
	guice: '4.2.2',
	immutables: '2.8.3',
}

require('./.ext/highlands/')
	.lib('//lib/javax/annotation', `com.google.code.findbugs:jsr305:3.0.1`)
	.lib('//lib/javax/inject', `javax.inject:javax.inject:1`)
	.lib('//lib/google/common', `com.google.guava:guava:${ver.guava}`)
	.lib('//lib/google/inject', `com.google.inject:guice:no_aop:${ver.guice}`, {
		srcs: `com.google.inject:guice:${ver.guice}`,
	})
	.lib('//lib/immutables/value:annotations', `org.immutables:value:annotations:${ver.immutables}`)
	.lib('//lib/immutables/value', `org.immutables:value:${ver.immutables}`, {
		// processor: 'org.immutables.processor.ProxyProcessor',
		srcs: [],
	})
	.lib('//lib/immutables/value:processor', [], {
		processor: 'org.immutables.processor.ProxyProcessor',
		deps: ['//lib/immutables/value'],
	})
	.lib('//lib/immutables/trees', `org.immutables:trees:${ver.immutables}`)
	.lib('//lib/immutables/ordinal', `org.immutables:ordinal:${ver.immutables}`)
	.lib('//lib/immutables/generator', `org.immutables:generator:${ver.immutables}`)
	.lib('//lib/immutables/generator:processor', [
		`org.immutables:generator-processor:luggage:${ver.immutables}`
	], {
		deps: ['//lib/google/common', '//lib/immutables/trees'],
		srcs: [],
	})
	.lib('//lib/postresql', [
		`org.postgresql:postgresql:42.2.12`,
	])
	.lib('//lib/junit', [
		`junit:junit:4.12`,
		`org.hamcrest:hamcrest-core:1.3`,
	])
	.run()
