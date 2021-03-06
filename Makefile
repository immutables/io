# This makefile is not used to build/compile source files to binaries on CI
# The purpose here is to provide organized shortcuts
# and entry points for running tools and invoking builds during development.
# (Makefiles expecially convenient for expressing and executing dependent goals
# where simple bash scripts would need to constantly check if something is already done.)
#
# Please, leave this file as brief and high level as possible. The makefile provides
# birds eye view of how this repository works, major things what can be done with it.
# What belongs here:
# * download, install, run and cleanup for tools, project file generation
# * invoke high-level build goals and tools

# phony targets are not bound to existense of directories or files by the same name
# and always re-executed (once per make call)
.PHONY: default up pub

.DEFAULT_GOAL := default
# explicitly requiring /bin/bash
# without this it's hard to setup any env vars in container
SHELL := /bin/bash

# here lower_snake_case variables are private to Makefile,
# UPPER_SNAKE_CASE ones we reserve for the ones coming from environment
# or special reserved Makefile variables.
# '=' lazy var, ':=' eagerly computed var, '?=' assign if not set

prereq_bin = git curl java python buck node
prereq_check = $(foreach bin,$(prereq_bin),$(if $(shell which $(bin)),,\
		$(error "No `$(bin)` found in PATH. see README.md")))

highlands_link = https://github.com/immutables/highlands/archive/v0.16.1.tar.gz

# The default is just to fetch, build, test all targets
default:
	$(prereq_check)
	@printf "\e[00;33m[fetch build test all]\e[00m\n"
	buck fetch //...
	buck build //...
	buck test //...

# Downloads Highlands scripts that updates dependencies and IDE projects
.ext/highlands:
	$(prereq_check)
	mkdir -p $@
	curl -L $(highlands_link) | tar xz -C $@ --strip-components=1

# Upgrades lib lock file and regens libs and symlinks, IDE projects
up: .ext/highlands
	$(prereq_check)
	@printf "\e[00;33m[updating dependencies, regen all]\e[00m\n"
	node up --uplock --lib --intellij --eclipse --trace

# Note: `cleanse` is a kind of "unsafe clean" - it would delete files which
# should be stored in the source control, such as generated library definition,
# dependency lock file, etc. It is expected that thouse will be regenerated
# (for example by calling "make up") before committing any changes.
# In addition, any tools/scripts downloaded by make targets and not stored
# in source controld will be removed and expected to be recreated/re-downloaded.
# Main purpose of this command is to try reset/redo all caches/generated project files
# when project "is not feeling well"
cleanse:
	$(prereq_check)
	@printf "\e[00;31m[cleansing, require regen before committing!]\e[00m\n"
	buck kill
	rm -fr buck-out
	rm -fr .idea
	rm -fr .ext
	rm -fr lib
	rm -fr .up.lock.json
	find . -type d -name '.out' -exec rm -fr {} +
	find . -type f -name 'pom.xml' -exec rm {} +

pub: .ext/highlands
	$(prereq_check)
	@printf "\e[00;33m[prepare pom files and artifacts for publishing]\e[00m\n"
	node up --publish --center pom.xml --trace
