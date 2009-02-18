
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
# Assumption: only one tar file for this tool
#

JAVA.home	:= $(JAVA_HOME)

ifdef JAVA.home
JAVA.home.specified = yes
else
JAVA.home   = $(VIEW_ROOT)/vob/tools/java
endif

ifndef NO_JDK_DOCS
    JAVA.doc = $(JAVA.home)/docs
endif

JAVA.bin     = $(JAVA.home)/bin
JAVA.lib     = $(JAVA.home)/lib
JAVA.rm	    := $(filter-out %akefile,$(filter-out %.tar,$(wildcard $(JAVA.home)/*)))
JAVA.tools   = $(JAVA.lib)/tools.jar

JAVAC	     = $(JAVA.bin)/javac
JAVA	     = $(JAVA.bin)/java
JAVAH	     = $(JAVA.bin)/javah
JAVAP	     = $(JAVA.bin)/javap
JAVADOC	     = $(JAVA.bin)/javadoc
JAR          = $(JAVA.bin)/jar
RMIC         = $(JAVA.bin)/rmic

JAVADOC.tar := $(JAVA.home)/*doc.tar
JAVADOC.tar := $(JAVA.home)/*doc.tar
JDK.tar	    := $(JAVA.home)/JDK*.tar

ifdef JAVA.home.specified

# we need to do nothing here so that "tools" is a defined label

tools::

else

all: tools

tools:: $(JAVA.bin) $(JAVA.doc)

$(JAVA.bin): $(JDK.tar)
	-rm -rf $(JAVA.rm)
	(cd $(JAVA.home) ; tar xf $(JDK.tar))
	touch $(JAVA.bin)

$(JAVA.doc): $(JAVADOC.tar)
	-rm -rf $(JAVA.doc)
	(cd $(JAVA.home) ; tar xf $(JAVADOC.tar))
	touch $(JAVA.doc)

clobber clobber.all::
	-rm -rf $(JAVA.rm)

endif
