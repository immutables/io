// uses Highlands downloaded into .ext
// then we include all the dependencies definitions from io/deps.js
require('./.ext/highlands/')
	.include(() => require('./io/deps.js'))
	.run()
