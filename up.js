const ver = {
	guava: '29.0-jre',
	asm: '8.0.1',
	immutables: '2.8.9-ea-1',
	guice: '4.2.2',
	jersey: '2.31',
	grizzly: '2.4.4',
	hk2: '2.6.1',
	slf4j: '1.7.26',
	jackson: '2.9.9',
}

// uses Highlands downloaded into .ext
require('./.ext/highlands/')
	.include(() => require('./io/deps.js'))
	.lib('//io/ecs/sample:pre', {
		internal: true,
		includeGeneratedSrcs: ':sample',
	})
	.run()
