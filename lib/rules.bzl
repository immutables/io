# Definition of macros/shortcut rules used for typical type of modules

_java_library_deps_default = [
]

_java_test_deps_default = [
  '//lib/junit:junit'
]

_kotlin_library_deps_default = [
  '//lib/kotlin/stdlib:stdlib'
]

_kotlin_test_deps_default = [
  '//lib/junit:junit'
]

def java_module(
    name,
    deps = [],
    test_deps = [],
    provided_deps = [],
    exported_deps = [],
    exported_provided_deps = [],
    plugins = [],
    visibility = None,
    maven_coords = None):
  native.java_library(
    name = name,
    srcs = native.glob(['src/**/*.java']),
    resources = native.glob(['src/**'], exclude = ['*.java']),
    resources_root = 'src',
    deps = _dedupe(_java_library_deps_default + deps),
    provided_deps = provided_deps,
    exported_deps = exported_deps,
    exported_provided_deps = exported_provided_deps,
    plugins = plugins,
    visibility = ['//...'] if not visibility else visibility,
    maven_coords = maven_coords,
  )
  native.java_test(
    name = 'test',
    srcs = native.glob(['test/**/*.java']),
    resources = native.glob(['test/**']),
    resources_root = 'test',
    deps = _dedupe([':' + name] + _java_test_deps_default + test_deps),
    plugins = plugins,
    vm_args = ['-ea', '-Dio.immutables.that.replace-error-message= '],
  )

def kotlin_module(
    name,
    deps = [],
    test_deps = [],
    provided_deps = [],
    exported_deps = [],
    exported_provided_deps = [],
    plugins = [],
    visibility = None,
    maven_coords = None):
  native.kotlin_library(
    name = name,
    srcs = native.glob(['src/**/*.kt']),
    resources = native.glob(['src/**'], exclude = ['*.kt']),
    resources_root = 'src',
    deps = _dedupe(_kotlin_library_deps_default + deps),
    provided_deps = provided_deps,
    exported_deps = exported_deps,
    exported_provided_deps = exported_provided_deps,
    plugins = plugins,
    visibility = ['//...'] if not visibility else visibility,
    maven_coords = maven_coords,
  )
  native.kotlin_test(
    name = 'test',
    srcs = native.glob(['test/**/*.kt']),
    resources = native.glob(['test/**']),
    resources_root = 'test',
    deps = _dedupe([':' + name] + _kotlin_test_deps_default + test_deps),
    plugins = plugins,
    vm_args = ['-ea'],
  )

def _dedupe(seq):
  seen = {}
  return [x for x in seq if not (x in seen or seen.update({x: ()}))]
