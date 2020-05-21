# Definition of macros/shortcut rules used for typical type of modules

_java_library_deps_default = [
]

_java_test_deps_default = [
  '//lib/junit:junit',
#  '//lib/immutables/that:that',
]

_java_test_vm_args = [
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+UseEpsilonGC',
  '-ea',
  '-Dio.immutables.that.replace-error-message= ',
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
    maven_coords = None,
		resources_add = []):
  native.java_library(
    name = name,
    srcs = native.glob(['src/**/*.java']),
    resources = native.glob(['src/**'], exclude = ['*.java']) + resources_add,
    resources_root = 'src',
    deps = _dedupe(_java_library_deps_default + deps),
    provided_deps = provided_deps,
    exported_deps = exported_deps,
    exported_provided_deps = exported_provided_deps,
    plugins = plugins,
    visibility = ['PUBLIC'] if not visibility else visibility,
    maven_coords = maven_coords,
  )
  native.java_test(
    name = 'test',
    srcs = native.glob(['test/**/*.java']),
    resources = native.glob(['test/**']),
    resources_root = 'test',
    deps = _dedupe([':' + name] + _java_test_deps_default + deps + test_deps),
    plugins = plugins,
    vm_args = _java_test_vm_args,
  )


def _dedupe(seq):
  seen = {}
  return [x for x in seq if not (x in seen or seen.update({x: ()}))]


def java_test_vm_args():
  return _java_test_vm_args


# for use in explicitly written kotlin_library
def kotlinc_args():
  return [
    '-java-parameters',
    '-nowarn',
    '-no-stdlib',
    '-no-reflect',
    '-jvm-target', native.read_config('java', 'target_level', '1.8')
  ]


# java_resources uses java_library and special labels (processed by IDE project generation tool)
# to create library of resources for the module, by default, if not set,
# resource root folder (resources_root) will be set to target name,
# but if it's nested, better just define it explicitly (resources_root = 'some/nested/folder')
def java_resources(
    name,
    test = False, # marks as test resources, but can be added to test and non-test rules as deps
    exported_deps = [], # only exported deps makes sense for resources which are not compiled
    resources = None, # derives from resources_root or can be specified
    resources_root = None, # defaults to name
    visibility = None,
):
  resources_root = resources_root if resources_root else name
  resources = resources if resources else native.glob([resources_root + '/**'])
  native.java_library(
    name = name,
    srcs = [],
    resources = resources,
    resources_root = resources_root,
    exported_deps = exported_deps,
    labels = ['ide_test_res'] if test else ['ide_res'],
    visibility = visibility,
  )
