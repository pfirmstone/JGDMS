
#/*
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
# 
#      http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#*/



#
# Basic variables
#
PWD		:= $(shell pwd)
TOP		:= $(shell (echo $(PWD)) | sed -e 's,/src.*,,')
SRC		:= $(TOP)/src
DOC		:= $(TOP)/doc
APIDOC		:= $(DOC)/api
APIDOCCHECK	:= $(DOC)/api-check
VIEW_ROOT	:= $(shell (echo $(PWD)) | sed -e 's,/vob.*,,')
export VIEW_ROOT
ifndef JAVA.home
JAVA.home	:= $(shell type java | sed -e 's,[^/]*\(.*\)/bin/java$$,\1,')
export JAVA.home
endif

CLASSDIR	:= $(TOP)/classes
JARSDIR		:= $(VIEW_ROOT)/vob/jive/lib
DLJARSDIR	:= $(VIEW_ROOT)/vob/jive/lib-dl
EXTJARSDIR	:= $(VIEW_ROOT)/vob/jive/lib-ext
QAJARSDIR	:= $(VIEW_ROOT)/vob/qa/lib
JIVE_DOC	:= $(VIEW_ROOT)/vob/jive/doc
MAKEFILE	:= GNUmakefile

# treat top of the source tree specially
ifeq ($(PWD),$(SRC))
THISDIR		:= .
CLASSDIR.this	:= $(CLASSDIR)
PKG		:= 
else
THISDIR		:= $(PWD:$(SRC)/%=%)
CLASSDIR.this	:= $(CLASSDIR)/$(THISDIR)
PKG		:= $(subst /,.,$(THISDIR)).
endif

# buildable files
#
# We need to select only the stubs from this package and
# subpackages, which is why we filter SERVER.stubs to contain only
# things that at least have THISDIR.

SERVER.paths	= $(subst .,/,$(sort $(SERVER.class)))
SERVER.classes	= $(SERVER.paths:%=$(CLASSDIR)/%.class)
SERVER.stubs	= $(filter $(CLASSDIR.this)/%,$(SERVER.paths:%=$(CLASSDIR)/%_Stub.class))

SERVER.IIOP.paths= $(subst .,/,$(sort $(SERVER.IIOP.class)))
SERVER.IIOP.stubs= $(filter $(CLASSDIR.this)/%,$(SERVER.IIOP.paths:%=$(CLASSDIR)/%_IIOP_Stub.class))

FILES.raw	:= $(sort $(shell find . -name '*.java' -print))
FILES.java	:= $(FILES.raw:./%=%)
FILES.class	:= $(FILES.java:%.java=$(CLASSDIR.this)/%.class)

THIS.java	:= $(wildcard *.java)
THIS.class	:= $(THIS.java:%.java=$(CLASSDIR.this)/%.class)
THIS.stubs	:= $(patsubst /$(CLASSDIR.this)/%,,$(SERVER.stubs))

# THIS.class must contain only those stubs for this package, so we filter
# out anything from any subpackage.  I can't figure out how to do this using
# gmake string manipulation -- there must be a way...

PACKAGES.raw	:= $(subst /,.,$(sort $(dir $(FILES.java))))
PACKAGES.list	:= $(addprefix $(PKG),$(PACKAGES.raw))
PACKAGES	:= $(PACKAGES.list:%...=%)
PACKAGES	:= $(PACKAGES:%.=%)
PACKAGES.doc	= $(sort $(strip $(filter-out $(NO_DOC),$(PACKAGES))))

JSK_PACKAGES :=								\
		com.sun.jini.admin					\
		com.sun.jini.config					\
		com.sun.jini.discovery					\
		com.sun.jini.discovery.kerberos				\
		com.sun.jini.discovery.plaintext			\
		com.sun.jini.discovery.ssl				\
		com.sun.jini.discovery.x500.sha1withdsa			\
		com.sun.jini.discovery.x500.sha1withrsa			\
		com.sun.jini.example.browser				\
		com.sun.jini.fiddler					\
		com.sun.jini.landlord					\
		com.sun.jini.lease					\
		com.sun.jini.logging					\
		com.sun.jini.lookup.entry				\
		com.sun.jini.mercury					\
		com.sun.jini.norm					\
		com.sun.jini.outrigger					\
		com.sun.jini.outrigger.logstore				\
		com.sun.jini.outrigger.snaplogstore			\
		com.sun.jini.phoenix					\
		com.sun.jini.proxy					\
		com.sun.jini.reggie					\
		com.sun.jini.resource					\
		com.sun.jini.start					\
		com.sun.jini.thread					\
		com.sun.jini.tool					\
		com.sun.jini.tool.envcheck				\
		net.jini.admin						\
		net.jini.core.discovery					\
		net.jini.core.entry					\
		net.jini.core.event					\
		net.jini.core.lease					\
		net.jini.core.lookup					\
		net.jini.core.transaction				\
		net.jini.core.transaction.server			\
		net.jini.discovery					\
		net.jini.event						\
		net.jini.lease						\
		net.jini.lookup						\
		net.jini.lookup.entry

JSK_SPEC_PACKAGES :=							\
		net.jini.activation					\
		net.jini.config						\
		net.jini.constraint					\
		net.jini.core.constraint				\
		net.jini.entry						\
		net.jini.export						\
		net.jini.id						\
		net.jini.iiop						\
		net.jini.io						\
		net.jini.io.context					\
		net.jini.jeri						\
		net.jini.jeri.connection				\
		net.jini.jeri.http					\
		net.jini.jeri.kerberos					\
		net.jini.jeri.ssl					\
		net.jini.jeri.tcp					\
		net.jini.jrmp						\
		net.jini.loader						\
		net.jini.loader.pref					\
		net.jini.security					\
		net.jini.security.policy				\
		net.jini.security.proxytrust				\
		net.jini.space						\
		net.jini.url.file					\
		net.jini.url.httpmd					\
		net.jini.url.https

DOC_JSK_JAVA_SOURCES :=							\
		com/sun/jini/mahalo/MahaloPermission.java

# The file that will hold the list of classes that need rebuilding

CLASS_LIST	:= /tmp/.class$(shell echo $$$$)
CLASS_LIST_XJCOV := $(CLASS_LIST).jcov
STUBS_LIST	:= $(subst class,stubs,$(CLASS_LIST))
STUBS11_LIST	:= $(subst class,stubs11,$(CLASS_LIST))
STUBS_IIOP_LIST	:= $(subst class,stubs_iiop,$(CLASS_LIST))

# how to compile things
JAVAC.echo	= echo javac $(JAVACFLAGS)
JAVAC.exec	= $(JAVAC) -d $(CLASSDIR) $(JAVACFLAGS) \
		   -classpath $(CLASSPATH) -sourcepath $(SOURCEPATH)
JAVAVERSION.exec= $(JAVA) -version

RMICFLAGS	= 
RMIC.echo	= echo rmic $(RMICFLAGS)
RMIC.exec	= $(RMIC) -d $(CLASSDIR) $(RMICFLAGS) -classpath $(CLASSPATH)

# The rest of the JDK we take from somplace else
#

# The tools directory
#
TOOLSDIR	= $(JTSK_ROOT)
TOOLS		= $(TOOLSDIR)/src
JARTOOLSDIR	= $(TOOLS)/com/sun/jini/tool

TOOLSCLASSES	= $(TOOLSDIR)/classes
CLASSDEP_EXEC   = $(JAVA) -cp $(TOOLSCLASSES):$(JAVA.tools) com.sun.jini.tool.ClassDep -files
MANIFESTDIR     = $(SRC)/manifest

# For soft link(s) that are necessary for compilation
#
JIVE_INC_PATH	= $(VIEW_ROOT)/vob/qa/src/GNUmakefile.inc
JIVE_INC_LINK	= $(VIEW_ROOT)/vob/jive/src/GNUmakefile.inc

# Compilation flags
#

ifndef JAVADEBUG
	JAVADEBUG = -g:lines,source,vars
endif

SOURCEFLAGS	= -source 1.4
JAVACFLAGS	= $(JAVADEBUG) -deprecation $(SOURCEFLAGS)
JAVADOCLEVEL	=
JDK_DOC		= http://java.sun.com/j2se/1.4.2/docs/api
JDK_PACKAGES	= $(JIVE_DOC)/j2se
EXIMPLFLAGS	= -tag com.sun.jini.impl:X:ignore
INCIMPLFLAGS	= -tag com.sun.jini.impl:pt:"Implementation Specifics:"
JAVADOCFLAGS	= -version $(JAVADOCLEVEL) $(SOURCEFLAGS) -breakiterator \
		  -linkoffline $(JDK_DOC) $(JDK_PACKAGES)
APIDOCFLAGS	= $(JAVADOCFLAGS) $(INCIMPLFLAGS) -serialwarn
DOCCHECKFLAGS	= $(JAVADOCLEVEL) $(SOURCEFLAGS)

ifndef DOCCHECKJAR
DOCCHECKJAR 	= /jini/files/apps/doccheck.jar
endif

# The classpath we run javac and rmic against
CLASSPATH	= $(CLASSDIR)

# The source path javac & javadoc should look for source files in if
# it can't find a class file for a given class.
SOURCEPATH      = $(SRC)

# Generic build rules
#
.SUFFIXES:
.SUFFIXES: .class .java .zip

# explicit single-run builds

# simple .class->.java rule
$(CLASSDIR.this)/%.class: $(SRC)/$(THISDIR)/%.java
	@echo '$?' >> $(CLASS_LIST)


# stubs and skeletons -- we must get impl class name from dependency
# class Note: we need to check the result of subst is non-null because
# sometimes (usually when the recursive make has already built the
# stubs) this rule will run even when $? evaluates to null.  Without
# the guard this would cause us to add a newline to STUBS_LIST which
# would cause problems when we run rmic.

$(CLASSDIR.this)/%Registry_Stub.class: $(CLASSDIR.this)/%Registry.class
	@set -e ;					      \
	classname='$(subst /,.,$(?:$(CLASSDIR)/%.class=%))' ; \
	if [ -n "$$classname" ]; then                         \
		echo $$classname >> $(STUBS11_LIST) ;         \
	fi

$(CLASSDIR.this)/%_Stub.class: $(CLASSDIR.this)/%.class
	@set -e ;					      \
	classname='$(subst /,.,$(?:$(CLASSDIR)/%.class=%))' ; \
	if [ -n "$$classname" ]; then                         \
		echo $$classname >> $(STUBS_LIST) ;           \
	fi

$(CLASSDIR.this)/%_IIOP_Stub.class: $(CLASSDIR.this)/%.class
	@set -e ;						\
	classname='$(subst /,.,$(?:$(CLASSDIR)/%.class=%))' ;	\
	if [ -n "$$classname" ]; then				\
		echo $$classname >> $(STUBS_IIOP_LIST) ;	\
	fi

# Class files for this package and all subpackages.
# Also, we put the target here so that just typing gmake
# will build the entire tree.
#
all: soft_links tools dirs classes stubs


# Add the includes here. This way other people can add their
# own variables here, and they can either add too or completely
# modify the above variables
#

include $(SRC)/GNUmakefile.allinc

INCLUDES	= $(shell find . -name GNUmakefile.inc -print) /dev/null

ifdef INCLUDES
    include $(INCLUDES)
endif

#
# If desired (ie, JCOV=1), instrument class files for code-coverage analysis:
#

ifndef BUILD_JCOV
    BUILD_JCOV = 1

    ifdef JCOV
	JCOV_FLAGS += -Xjcov
    endif
endif

# ============================================================
#				Targets
# ============================================================


# Just the class files for this package and all subpackages
#
classes: soft_links dirs init_java_list $(FILES.class) build_java_list

.PHONY: classes init_java_list build_java_list

init_java_list:
	@-rm -f $(CLASS_LIST)

# The recursion in this rule is because gmake is not always building all the
# necessary stubs if the stubs are built in the same run as the class that
# creates it.  This is odd, because when run with -d, gmake does the right
# thing.  This bug may be related to the one descirbed for the .DEFAULT rule

build_java_list:

	@##
	@## The CLASS_LIST_XJCOV file contains only those files from 
	@## the CLASS_LIST file from jini/benchmark.
	@##

	@set -e ;								\
	 if [ -f $(CLASS_LIST) ]; then						\
	    $(JAVAVERSION.exec) ;						\
										\
	    sed -n '/\/jini\/benchmark\//p' $(CLASS_LIST) > $(CLASS_LIST_XJCOV) ;\
										\
	    num=`wc -l < $(CLASS_LIST) | sed 's/[ 	]//g'` ;		\
	    num_xjcov=`wc -l < $(CLASS_LIST_XJCOV) | sed 's/[ 	]//g'` ;	\
										\
	    if [ "$$num" != "0" ]; then						\
		$(JAVAC.echo) $(JCOV_FLAGS) "[$$num files]:" ;			\
		sed -e 's/^/    /' $(CLASS_LIST) ;				\
		$(JAVAC.exec) $(JCOV_FLAGS) @$(CLASS_LIST) ;			\
	    fi;									\
										\
	    if [ "$$num_xjcov" != "0" ]; then					\
		$(JAVAC.echo) "[$$num_xjcov files]:" ;				\
		sed -e 's/^/    /' $(CLASS_LIST_XJCOV);				\
		$(JAVAC.exec) @$(CLASS_LIST_XJCOV) ;				\
	    fi;									\
										\
	    $(MAKE) --no-print-directory stubs ;				\
										\
	    rm -f $(CLASS_LIST) $(CLASS_LIST_XJCOV) ;				\
	 fi

# The stubs and skeletons for this package and all subpackages
#
stubs: init_stub_list $(SERVER.stubs) $(SERVER.IIOP.stubs) build_stub_list

.PHONY: classes init_stub_list build_stub_list

# Otherwise gmake thinks that these were only built to get at the stubs,
# and are therefore removable intermediate files (!)
.PRECIOUS: $(SERVER.classes)

init_stub_list:
	@-rm -f $(STUBS_LIST) $(STUBS11_LIST) $(STUBS_IIOP_LIST)

build_stub_list:
	@set -e ;							\
	 if [ -s $(STUBS_LIST) ]; then					\
	    $(RMIC.echo) "[Building 1.2 stub files]:" ;			\
	    sort $(STUBS_LIST) | sed -e 's/^/    /' ;			\
	    $(RMIC.exec) -v1.2 `cat $(STUBS_LIST)`;			\
	    rm -f $(STUBS_LIST) ;					\
	 fi ;								\
	 if [ -s $(STUBS11_LIST) ]; then				\
	    $(RMIC.echo) "[Building 1.1 stub files]:" ;			\
	    sort $(STUBS11_LIST) | sed -e 's/^/    /' ;			\
	    $(RMIC.exec) -v1.1 `cat $(STUBS11_LIST)`;			\
	    rm -f $(STUBS11_LIST) ;					\
	 fi ;								\
	 if [ -s $(STUBS_IIOP_LIST) ]; then				\
	    $(RMIC.echo) "[Building IIOP stub files]:" ;		\
	    sort $(STUBS_IIOP_LIST) | sed -e 's/^/    /' ;		\
	    $(RMIC.exec) -iiop `cat $(STUBS_IIOP_LIST)`;		\
	    rm -f $(STUBS_IIOP_LIST) ;					\
	 fi

# Class files for this package
#
this: soft_links dirs init_java_list $(THIS.class) build_java_list
.PHONY: this

soft_links: $(JIVE_INC_LINK)
.PHONY: soft_links

$(JIVE_INC_LINK):
	@ln -s $(JIVE_INC_PATH) $(JIVE_INC_LINK)

dirs: $(CLASSDIR)
.PHONY: dirs

$(CLASSDIR) $(JARSDIR) $(DLJARSDIR) $(EXTJARSDIR) $(QAJARSDIR) $(APIDOC):
	mkdir -p $@
	@test -d $@ || (echo no $@ ; exit 1)

jartools::

#
# must defer building tool wrappers until platform jars exist
#
all.jars jars.all:
	@cd $(VIEW_ROOT)/vob/qa/src; $(MAKE) jars

include $(VIEW_ROOT)/vob/tools/java/GNUmakefile

# javadoc files
#
#JAVADOC	= CLASSPATH=$(CLASSPATH):../javadoc java sun.tools.javadoc.Main

# The exit status of javadoc is ignored because of a bug -- it complains
# about a private interface that rmic and javac accept.  Unfortunately, if
# other errors show up this will just skip over them, but generally javadoc
# only generates warnings -- errors are for source code errors, which should
# have already been caught during compilation.

.PHONY: doc
doc: $(FILES.java) $(MAKEFILE)

	@rm -fr $(APIDOC)
	@mkdir -p $(APIDOC)
	@echo javadoc $(APIDOCFLAGS) -d $(APIDOC) \\
	@echo $(JSK_PACKAGES) $(JSK_SPEC_PACKAGES) $(DOC_JSK_JAVA_SOURCES) | awk '{ for (i = 1; i <= NF; i++) print "   ", $$i; }'
	@-$(JAVADOC) $(APIDOCFLAGS)					\
           -classpath $(CLASSPATH) -sourcepath $(SOURCEPATH):$(TOOLS)	\
           -d $(APIDOC)							\
	   $(JSK_PACKAGES)						\
	   $(JSK_SPEC_PACKAGES)						\
	   $(DOC_JSK_JAVA_SOURCES)

apicheck:
	@rm -fr $(APIDOCCHECK)
	@mkdir -p $(APIDOCCHECK)
	@echo javadoc $(DOCCHECKFLAGS) -d $(APIDOCCHECK) \\
	@echo $(PACKAGES.doc) | awk '{ for (i = 1; i <= NF; i++) print "   ", $$i; }'
	@-$(JAVADOC) $(DOCCHECKFLAGS)                      \
           -doclet com.sun.tools.doclets.doccheck.DocCheck \
           -docletpath $(DOCCHECKJAR) \
           -classpath $(CLASSPATH) -sourcepath $(SOURCEPATH) \
           -d $(APIDOCCHECK) $(PACKAGES.doc)

# Clean all class files for this package and all subpackages
#
clean clobber clean.all clobber.all::
	-rm -rf $(CLASSDIR.this)/
	-[ -f core ] && rm -f core

clean.jars::
	-rm -rf $(JARSDIR)/
	-rm -rf $(DLJARSDIR)/
	-rm -rf $(EXTJARSDIR)/

clean.doc clobber clobber.all::
	-rm -rf $(APIDOC) $(APIDOCCHECK)

.PHONY: clean clobber clean.all clobber.all

# Clean all class files for this package
#
clean.this clobber.this::
	-rm -f $(CLASSDIR.this)/*.class
	-[ -f core ] && rm -f core

.PHONY: clean.this clobber.this

# Print out the classpath we are using to build against
#
classpath:
	@echo $(CLASSPATH)

.PHONY: classpath

# Print out this message
#
what:
	@$(TOOLS)/targets 

.PHONY: what

vars:
	@echo PKG $(PKG)
	@echo PACKAGES.raw $(PACKAGES.raw)
	@echo PACKAGES.list $(PACKAGES.list)
	@echo PACKAGES $(PACKAGES)

.PHONY: vars

.DEFAULT:
	@echo "*** Unknown target: $@ ('make what' shows targets)";

java-version:
	@$(JAVAVERSION.exec)
