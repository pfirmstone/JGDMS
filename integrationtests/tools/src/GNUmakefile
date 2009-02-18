
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



INSTALL.root	:= /jini/files/
CDIR		:= $(INSTALL.root)/atria/clearcmds
DIRS		:= $(CDIR) $(VIEW_ROOT)/vob/tools/bin
MAKE.tar	:= $(shell echo make-*.tar)
MAKE.dir	:= $(MAKE.tar:%.tar=%)
JTOOLSDIR	:= $(VIEW_ROOT)/vob/tools/src/com/sun/jini/tool

SCRIPTS	= mergebranch copyright targets clearcmd vob.pl stampcode frame.pl

all: jtools

jtools:
	@cd $(JTOOLSDIR); $(MAKE)

quick:
	@for dir in $(DIRS); do $(MAKE) CDIR=$$dir install.$@; done

TGTSCRIPTS = $(SCRIPTS:%=$(CDIR)/%)

# duplicate copy is purposeful -- clearcmd -l removes everything but itself,
# so we have to put everything else back.  This is inefficient, but trivially
# so right now.

install install.all: install.clearcmd install.make

# we do the copy twice because clearcmd -l removes everything *but* clearcmd
install.clearcmd:
	-rm -f $(TGTSCRIPTS)
	test -d $(CDIR) || mkdir $(CDIR)
	cp $(SCRIPTS) $(CDIR)
	(cd $(CDIR) ; ./clearcmd -l)
	cp $(filter-out clearcmd,$(SCRIPTS)) $(CDIR)

install.quick:
	chmod +w $(TGTSCRIPTS)
	cp $(SCRIPTS) $(CDIR)
	chmod -w $(TGTSCRIPTS)

install.make:
	(cd $(MAKE.dir) ; $(MAKE) install)

make: $(MAKE.dir)
	(cd $(MAKE.dir) ; $(MAKE))

$(MAKE.dir): clean.make $(MAKE.tar)
	tar xf $(MAKE.tar)
	(cd $(MAKE.dir) ; ./configure --prefix=$(INSTALL.root) --program-transform-name=s/make/gmake/)
	(cd $(MAKE.dir) ; patch < ../make-patch)

clean.make:
	-rm -rf make-*[0-9]

JINI_CLASSES	= $(VIEW_ROOT)/vob/jive/classes
JARSDIR         = $(VIEW_ROOT)/vob/jive/lib
CLASSDIR        = $(VIEW_ROOT)/vob/tools/classes

clean clobber clean.all clobber.all::
	-rm -rf $(CLASSDIR)/
	-[ -f core ] && rm -f core
	-rm -f $(JARSDIR)/tools.jar

$(JARSDIR):
	mkdir -p $@
	@test -d $@ || (echo no $@ ; exit 1)

include $(VIEW_ROOT)/vob/tools/src/GNUmakefile.allinc
include $(VIEW_ROOT)/vob/tools/java/GNUmakefile
#include $(VIEW_ROOT)/vob/tools/odi/GNUmakefile

checkser:
	@echo Classes missing serialVersionUID:
	@$(JAVA) -cp $(CLASSDIR):$(JINI_CLASSES):$(ODI.home)/pro.zip com.sun.jini.tool.CheckSer $(JINI_CLASSES) | grep -v com.sun.jini.example | grep -v com.sun.jini.benchmark
