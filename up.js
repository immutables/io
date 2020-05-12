// See https://github.com/immutables/highlands/blob/master/README.md
// In order to download required scripts and run full regeneration use `make up`
// after required scripts downloaded, you can use `node up --lib --intellij`
// or other options (see readme link)

require('./.ext/highlands/')
	.include(() => require('./io/deps.js'))
	.run()
