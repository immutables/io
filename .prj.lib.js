let childProcess = require('child_process')
let fs = require('fs')
let path = require('path')

const REPO1 = 'https://repo1.maven.org/maven2/'
const REPO_SONATYPE = 'https://oss.sonatype.org/content/groups/staging/'
const REPO = REPO1
const DIRN = __dirname.split('/').pop()
const LIB_BUCK = path.join(__dirname, 'lib', 'BUCK')
const EXT = {
	jar: '.jar',
	src: '-sources.jar',
	sha1: '.sha1',
}

function exec(cmd) {
	console.log(`> ${cmd}`)
  return childProcess.execSync(cmd, {encoding: 'utf-8'})
}

class Gav {
	constructor(string) {
		this.data = string.split(':')
	}

	get group() {
		let [group, artifact, version, classifier] = this.data
		return group
	}

  get artifact() {
		let [group, artifact, version, classifier] = this.data
		return artifact
	}

  get version() {
		let [group, artifact, version, classifier] = this.data
		return version
	}

  get classifier() {
		let [group, artifact, version, classifier] = this.data
		return classifier
	}

	get filename() {
		return [
			this.artifact,
			this.version,
			this.classifier
		].filter(Boolean).join('-')
	}

	get path() {
		return [
			this.group.replace(/\./g, '/'),
			this.artifact,
			this.version,
			this.filename
		].join('/')
	}

	toString() {
		return this.data.filter(Boolean).join(':')
	}
}

function readLibArtifacts(prj) {
	let artifacts = []
	for (let key in prj.lib) {
		let gav = new Gav(prj.lib[key])
		artifacts.push({
			key,
			gav,
			deps: (prj.deps || {})[key],
			nocp: (prj.classpath_exclude || {})[key] === true
		})
	}
	return artifacts
}

function generateClasspath(libs, gen, apt, src) {
	let srcEntries = [], libEntries = [], genEntries = []

	for (let s of src) {
		srcEntries.push(`
  <classpathentry kind="src" path="${s}"/>`)
	}

	for (let l of libs.filter(v => !v.nocp)) {
		libEntries.push(`
  <classpathentry kind="lib" path="buck-out/gen/lib/${l.key}_jar/${l.gav.filename}${EXT.jar}" sourcepath="buck-out/gen/lib/${l.key}_src/${l.gav.filename}${EXT.src}"/>`)
	}

	for (let g of gen) {
		genEntries.push(`
  <classpathentry kind="src" path="${g}">
    <attributes>
		  <attribute name="optional" value="true"/>
    </attributes>
	</classpathentry>`)
	}

	let classpath = `<?xml version="1.0" encoding="UTF-8"?>
<classpath>${srcEntries.join('')}${genEntries.join('')}
  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.8"/>${libEntries.join('')}
  <classpathentry kind="output" path=".classes"/>
</classpath>
	`

	fs.writeFileSync(path.join(__dirname, '.classpath'), classpath, 'utf-8')

	let factoryEntries = []

	for (let v of apt) {
		let p = genjar(v)
		if (p instanceof Array) {
			libs.filter(v => v.key == p[1]).map(v => `
<factorypathentry kind="WKSPJAR" id="/${DIRN}/buck-out/gen/lib/${v.key}_jar/${v.gav.filename}${EXT.jar}" enabled="true" runInBatchMode="false"/>`).forEach(e => factoryEntries.push(e))
		} else {
			factoryEntries.push(`
  <factorypathentry kind="WKSPJAR" id="/${DIRN}/${p}" enabled="true" runInBatchMode="false"/>`)
		}
	}

	let factorypath = `<?xml version="1.0" encoding="UTF-8"?>
<factorypath>${factoryEntries}
</factorypath>
`

	fs.writeFileSync(path.join(__dirname, '.factorypath'), factorypath, 'utf-8')
}

// unused, the strategy when jar files are in source control
function downloadAndGenerateLibs(libs) {
	exec(`rm -rf .newlib; mkdir .newlib`)

	for (let {gav} of libs) {
		exec(`curl ${REPO}${gav.path}${EXT.jar} > .newlib/${gav.filename}${EXT.jar}`)
		exec(`curl ${REPO}${gav.path}${EXT.srcJar} > .newlib/${gav.filename}${EXT.src}`)
	}

	let libRules = ''
	for (let l of libs) {
		libRules += `
prebuilt_jar(
  name = '${l.key}',
  binary_jar = '${l.gav.filename}${EXT.jar}',
  source_jar = '${l.gav.filename}${EXT.src}',
  visibility = ['//...'],${ l.deps ? `
  deps = [${l.deps.map(d => `':${d}'`)}]` : '' }
)
		`
	}

	fs.writeFileSync(path.join(__dirname, '.newlib', 'BUCK'), libRules, 'utf-8')

	exec(`rm -rf lib; mv .newlib lib`)
}

function generateAndFetchLibs(libs) {
	let sha1Jar = {}, sha1Src = {}

	for (let {gav} of libs) {
		sha1Jar[gav] = exec(`curl ${REPO}${gav.path}${EXT.jar}${EXT.sha1}`).trim()
		sha1Src[gav] = exec(`curl ${REPO}${gav.path}${EXT.src}${EXT.sha1}`).trim()
	}

	let libRules = ''
	for (let l of libs) {
		libRules += `
prebuilt_jar(
  name = '${l.key}',
  binary_jar = ':${l.key}_jar',
  source_jar = ':${l.key}_src',
  visibility = ['//...'],${ l.deps ? `
  deps = [${l.deps.map(d => `':${d}'`)}]` : '' }
)

remote_file(
  name = '${l.key}_jar',
  out = '${l.gav.filename}${EXT.jar}',
  url = '${REPO}${l.gav.path}${EXT.jar}',
  sha1 = '${sha1Jar[l.gav]}'
)

remote_file(
  name = '${l.key}_src',
  out = '${l.gav.filename}${EXT.src}',
  url = '${REPO}${l.gav.path}${EXT.src}',
  sha1 = '${sha1Src[l.gav]}'
)
`
	}

	fs.writeFileSync(LIB_BUCK, libRules, 'utf-8')

	exec(`buck fetch //...`)
}

exports.project = function(prj) {
	let libs = readLibArtifacts(prj)
	generateAndFetchLibs(libs)
	generateClasspath(libs, prj.gen || [], prj.eclipse_apt || {}, prj.src || ['src'])
}

exports.gensrc = function(buckTarget) {
	let [path, goal] = buckTarget.split(':')
	path = path.indexOf('//') == 0
			? path.slice(2)
			: path

	return `buck-out/annotation/${path}/__${goal}_gen__`
}

function genjar(buckTarget) {
	let [path, goal] = buckTarget.split(':')
	path = path.indexOf('//') == 0
			? path.slice(2)
			: path

  if (path == 'lib') {
		return [path, goal]
	}

	return `buck-out/gen/${path}/${goal}.jar`
}
