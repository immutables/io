// uses Highlands downloaded into .ext
// then we include all the dependencies definitions from io/deps.js
//require('./.ext/highlands/')
require('./.highlands/')
	.include(() => require('./io/deps.js'))
	.lib('//io/ecs/sample:pre', {
		internal: true,
		includeGeneratedSrcs: ':sample',
	})
	.run()
