const ver = {
	guava: '29.0-jre',
	asm: '8.0.1',
	immutables: '2.9.0-beta2',
	guice: '4.2.2',
	jersey: '2.31',
	grizzly: '2.4.4',
	hk2: '2.6.1',
	slf4j: '1.7.26',
	jackson: '2.9.9',
}

module.exports = function(up) { up
	.lib('//lib/javax/annotation', `com.google.code.findbugs:jsr305:3.0.1`)
	.lib('//lib/javax/jaxrs', [
		`jakarta.ws.rs:jakarta.ws.rs-api:2.1.5`,
		`jakarta.annotation:jakarta.annotation-api:1.3.4`,
	])
	.lib('//lib/google/common:failureaccess', `com.google.guava:failureaccess:1.0`)
	.lib('//lib/google/common', `com.google.guava:guava:${ver.guava}`, {
		deps: ['//lib/google/common:failureaccess'],
	})
	.lib('//lib/square/okio', `com.squareup.okio:okio:1.16.0`)
	.lib('//lib/square/moshi', `com.squareup.moshi:moshi:1.9.2`, {
		deps: ['//lib/square/okio'],
	})
	.lib('//lib/square/javapoet', `com.squareup:javapoet:1.12.1`)
	.lib('//lib/immutables/value:annotations', `org.immutables:value:annotations:${ver.immutables}`)
	.lib('//lib/immutables/value', `org.immutables:value:${ver.immutables}`, {
		srcs: [],
	})
	.lib('//lib/immutables/data', `org.immutables:data:${ver.immutables}`)
	.lib('//lib/immutables/value:processor', {
		processor: 'org.immutables.processor.ProxyProcessor',
		deps: ['//lib/immutables/value'],
	})
	.lib('//lib/immutables/gson', `org.immutables:gson:${ver.immutables}`)
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
	.lib('//lib/immutables/generator:templater', {
		processor: 'org.immutables.generator.processor.Processor',
		deps: ['//lib/immutables/generator:processor'],
	})
	.lib('//lib/postresql', `org.postgresql:postgresql:42.2.12`)
	.lib('//lib/junit', [
		`junit:junit:4.13.1`,
		`org.hamcrest:hamcrest-core:1.3`,
	])
	.lib('//lib/javax/inject', `javax.inject:javax.inject:1`)
	.lib('//lib/javax/jaxb', 'javax.xml.bind:jaxb-api:2.3.0')
	.lib('//lib/javax/validation', 'javax.validation:validation-api:2.0.1.Final')
	.lib('//lib/javax/activation', 'javax.activation:javax.activation-api:1.2.0')
	.lib('//lib/google/inject', `com.google.inject:guice:no_aop:${ver.guice}`, {
		deps: ['//lib/javax/inject'],
		srcs: `com.google.inject:guice:${ver.guice}`,
	})
	.lib('//lib/glassfish/jersey:common', [
		`org.glassfish.jersey.core:jersey-client:${ver.jersey}`,
		`org.glassfish.jersey.core:jersey-common:${ver.jersey}`,
		`org.glassfish.jersey.inject:jersey-hk2:${ver.jersey}`,
		`org.glassfish.hk2:hk2-locator:${ver.hk2}`,
		`org.glassfish.hk2.external:aopalliance-repackaged:${ver.hk2}`,
		`org.glassfish.hk2:hk2-api:${ver.hk2}`,
		`org.glassfish.hk2:hk2-utils:${ver.hk2}`,
	], {
		deps: [
			'//lib/javax/jaxrs',
			'//lib/javax/inject',
			'//lib/javax/validation',
			'//lib/javax/activation',
			'//lib/javax/jaxb',
		],
	})
	.lib('//lib/glassfish/jersey', [
		`org.glassfish.jersey.core:jersey-server:${ver.jersey}`,
		`org.glassfish.jersey.containers:jersey-container-grizzly2-http:${ver.jersey}`,
		`org.glassfish.grizzly:grizzly-http:${ver.grizzly}`,
		`org.glassfish.grizzly:grizzly-http-server:${ver.grizzly}`,
		`org.glassfish.grizzly:grizzly-framework:${ver.grizzly}`,
		`org.glassfish:jakarta.el:3.0.2`,
		`org.javassist:javassist:3.22.0-CR2`,
	], {
		deps: ['//lib/glassfish/jersey:common']
	})
	.lib('//lib/glassfish/jersey:oauth2', `org.glassfish.jersey.security:oauth2-client:${ver.jersey}`)
	.lib('//lib/slf4j', [
		`org.slf4j:slf4j-api:${ver.slf4j}`,
		`org.slf4j:slf4j-jdk14:${ver.slf4j}`,
	])
	.lib('//lib/kafka', [
		`org.apache.kafka:kafka-clients:2.5.0`,
	], {
		deps: [
			'//lib/jackson',
			'//lib/slf4j',
		]
	})
	.lib('//lib/jackson', [
		`com.fasterxml.jackson.core:jackson-annotations:${ver.jackson}`,
		`com.fasterxml.jackson.core:jackson-core:${ver.jackson}`,
		`com.fasterxml.jackson.core:jackson-databind:${ver.jackson}`,
		`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${ver.jackson}`,
		`org.yaml:snakeyaml:1.18`,
		`com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${ver.jackson}`,
	])
	.lib('//lib/mockito', [
		`org.mockito:mockito-core:2.28.2`,
		`net.bytebuddy:byte-buddy:1.10.9`,
		`net.bytebuddy:byte-buddy-agent:1.10.9`,
		`org.objenesis:objenesis:2.6`,
	])
	.lib('//lib/atlassian/commonmark', `com.atlassian.commonmark:commonmark:0.15.2`)
}
