
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


# {{ ifdefs

#
# Allow for builds that need the build info file in the package:
#

ifdef QA_TESTING
	
	BUILD_INFO_TARGET	= build.info
else
	BUILD_INFO_TARGET	= 
endif

# }}

# COMMANDS
#VERBOSE_FLAG		= v
VERBOSE_FLAG		=
CPIO			= /bin/cpio -dumpL$(VERBOSE_FLAG)
ZIP			= zip
ZIPFLAGS		= -q
UNZIP			= unzip
UNZIP_LIST		= $(UNZIP) -l
CHECKSUM		= /usr/bin/sum
VERBOSE			=

# KITTING INFO
VOB			= $(VIEW_ROOT)/vob
JIVE_VOB		= $(VIEW_ROOT)/vob/jive
QA_VOB			= $(VIEW_ROOT)/vob/qa
VOB_JIVE		= $(JIVE_VOB)
VOB_QA			= $(QA_VOB)
KIT_TOP			= $(JIVE_VOB)/kit
KIT_QA			= $(KIT_TOP)/qa_tools

BUILD_INFO.txt		= build_info.txt
PRODUCT_INFO_FILE	= product_info.sh

VERSION_SUFFIX		=
VERSION			= 2.1
VERSTRING		= $(subst .,_,$(VERSION))
JDCVERSTRING		= $(subst .,,$(VERSION))
PROD			= jiniqa
PRODNAME		= $(PROD)$(VERSTRING)
PRODNAME_HAR		= $(PRODNAME)_har
PRODNAME_SRC		= $(PRODNAME)_src
PRODNAME_HARNESS	= $(PRODNAME)_harness
PRODNAME_TESTS		= $(PRODNAME)_tests
PRODNAME_JTREG		= $(PRODNAME)_jtreg

JINI_VERSION	       := $(shell egrep '^VERSION[ 	][ 	]*=' $(JIVE_VOB)/src/GNUmakefile.inc | cut -d'=' -f2 | sed 's|[  ]||g')
JINI_VERSTRING		= $(subst .,_,$(JINI_VERSION))
JINI_PROD	       := $(shell egrep '^PROD[ 	][ 	]*=' $(JIVE_VOB)/src/GNUmakefile.inc | cut -d'=' -f2 | sed 's|[  ]||g')
JINI_PRODNAME		= $(JINI_PROD)$(JINI_VERSTRING)

RELEASE_JINIQA.zip	= $(PROD)$(JDCVERSTRING).zip
RELEASE_JINIQA_CHKSUM	= $(PROD)$(JDCVERSTRING).checksum

RELEASE_JINIQA_HAR.zip	= $(PRODNAME_HAR).zip
RELEASE_JINIQA_SRC.zip	= $(PRODNAME_SRC).zip
RELEASE_JINIQA_HARNESS.zip	= $(PRODNAME_HARNESS).zip
RELEASE_JINIQA_TESTS.zip	= $(PRODNAME_TESTS).zip
RELEASE_JINIQA_JTREG.zip	= $(PRODNAME_JTREG).zip

QA_POLICY_DIR		= $(TOP)/harness/policy
COM_SUN_RESOURCES_DIR	= com/sun/jini/qa/resources
TEST_RESOURCES_DIR	= com/sun/jini/test/resources
E2E_RESOURCES_DIR	= com/sun/jini/test/impl/end2end/resources

QAHARNESS_PROP_FILE	= $(TEST_RESOURCES_DIR)/qaHarness.prop
DEPLOYMENT_PROP_FILE	= $(COM_SUN_RESOURCES_DIR)/basicDeployment.prop

LIB_DIR			= lib
COM_DIR			= com
DOC_DIR			= doc
OVERTURE_TEST_DIR	= jtreg
OVERTURE_DIR		= overture
POLICY_DIR		= harness/policy
SOURCE_DIR		= src
SRC_DIR			= src
CONFIGS_DIR		= harness/configs
TRUST_DIR		= harness/trust

REL_TOP			= /tmp
REL_DIR			= $(REL_TOP)/$(PRODNAME)

REL_DIR_HAR		= $(REL_TOP)/$(PRODNAME_HAR)
REL_DIR_SRC		= $(REL_TOP)/$(PRODNAME_SRC)
REL_DIR_HARNESS		= $(REL_TOP)/$(PRODNAME_HARNESS)
REL_DIR_TESTS		= $(REL_TOP)/$(PRODNAME_TESTS)
REL_DIR_JTREG		= $(REL_TOP)/$(PRODNAME_JTREG)



API_CPYRT_STR		= Licensed to the Apache Software Foundation (ASF) under one							\
			  or more contributor license agreements.  See the NOTICE file							\
			  distributed with this work for additional information								\
			  regarding copyright ownership. The ASF licenses this file							\
			  to you under the Apache License, Version 2.0 (the								\
			  "License"); you may not use this file except in compliance							\
			  with the License. You may obtain a copy of the License at							\
																	\
			       <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>	\
																	\
			  Unless required by applicable law or agreed to in writing, software						\
			  distributed under the License is distributed on an "AS IS" BASIS,						\
			  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.					\
			  See the License for the specific language governing permissions and						\
			  limitations under the License.

API_CPYRT		= "$(API_CPYRT_STR)" 

TITLE_STR		= Jini(TM) Test Harness V2

JAVADOC_ARGS		= $(JAVADOCFLAGS)							\
			  -doctitle	"$(TITLE_STR)"						\
			  -d		doc/api							\
			  -bottom	$(API_CPYRT)						\
			  -overview	overview.html						\
			  -sourcepath	$(VIEW_ROOT)/vob/jive/src:$(VIEW_ROOT)/vob/qa/src:$(VIEW_ROOT)/vob/tools/src	\
			  -windowtitle	"$(TITLE_STR)"


##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.PHONY: release kit.clean kit.setup clean checksum

release::	trust			\
		jars			\
		kit.clean		\
		kit.setup		\
		jiniqa			\
		harness			\
		tests			\
		jtregtests		\
		checksum		\
		jiniqa_src		\
		product.info		\
		$(BUILD_INFO_TARGET)

	@echo "Done with release build"

#		jiniqa_har		\

jiniqa: qa.doc
	mkdir -p $(REL_DIR)/$(DOC_DIR)						\
		 $(REL_DIR)/$(LIB_DIR)						\
		 $(REL_DIR)/$(OVERTURE_DIR)					\
		 $(REL_DIR)/$(POLICY_DIR)					\
		 $(REL_DIR)/$(TRUST_DIR)					\
		 $(REL_DIR)/$(SOURCE_DIR)					\
		 $(REL_DIR)/$(COM_SUN_RESOURCES_DIR)				\
		 $(REL_DIR)/$(TEST_RESOURCES_DIR)				\
		 $(REL_DIR)/$(CONFIGS_DIR)

	cp -p 	$(QA_VOB)/lib/qa1*.jar						\
		$(QA_VOB)/lib/fullloggingfilter.jar				\
		$(QA_VOB)/lib/harness-killer.jar				\
		$(QA_VOB)/lib/harness-killer-dl.jar				\
		$(QA_VOB)/lib/jiniharness.jar				\
		$(QA_VOB)/lib/jinitests.jar				\
		$(QA_VOB)/lib/mergedpolicyprovider.jar				\
		$(QA_VOB)/lib/nonactivatablegroup.jar				\
		$(QA_VOB)/lib/nonactivatablegroup-dl.jar			\
		$(JIVE_VOB)/lib/tools.jar					\
		$(REL_DIR)/$(LIB_DIR)


	cp -p	$(QA_POLICY_DIR)/* $(REL_DIR)/$(POLICY_DIR)

	cd $(QA_VOB);								\
	tar hcf - harness/configs | (cd $(REL_DIR) ; tar xvf -)
	chmod -R a+rw $(REL_DIR)/$(CONFIGS_DIR)

	cd $(QA_VOB);								\
	tar hcf - harness/trust | (cd $(REL_DIR) ; tar xvf -)
	chmod -R a+rw $(REL_DIR)/$(TRUST_DIR)

	cd $(QA_VOB)/src;	                                                \
	tar hcf - doc      | (cd $(REL_DIR) ; tar xvf -)

	cd $(QA_VOB)/src;	                                                \
	tar hcf - com/sun/jini/qa com/sun/jini/tests manifest GNU* | (cd $(REL_DIR)/$(SOURCE_DIR) ; tar xvf -)

	cd $(QA_VOB)/src;	                                                \
	sed -e 's|^com.sun.jini.jsk.home=.*|com.sun.jini.jsk.home=/files/$(JINI_PRODNAME)|'		\
	    -e 's|^com.sun.jini.qa.home=.*|com.sun.jini.qa.home=/files/$(PRODNAME)|'		\
	    -e 's|^com.sun.jini.test.home=.*|com.sun.jini.test.home=/files/$(PRODNAME)|'		\
		$(DEPLOYMENT_PROP_FILE)						\
		> $(REL_DIR)/$(DEPLOYMENT_PROP_FILE)

	cd $(QA_VOB);								\
	find $(OVERTURE_TEST_DIR) -depth -print				      | \
		$(CPIO) $(REL_DIR)/$(OVERTURE_DIR)

	cd $(REL_TOP);								\
	$(ZIP) -q -n .class -r $(KIT_QA)/$(RELEASE_JINIQA.zip) $(PRODNAME)


##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

harness: qa.doc
	mkdir -p $(REL_DIR_HARNESS)/$(LIB_DIR)						\
		 $(REL_DIR)/$(COM_SUN_RESOURCES_DIR)				\
		 $(REL_DIR_HARNESS)/$(TRUST_DIR)

	cp -p 	$(QA_VOB)/lib/harness-killer.jar				\
		$(QA_VOB)/lib/harness-killer-dl.jar				\
		$(QA_VOB)/lib/jiniharness.jar				\
		$(QA_VOB)/lib/mergedpolicyprovider.jar				\
		$(QA_VOB)/lib/nonactivatablegroup.jar				\
		$(QA_VOB)/lib/nonactivatablegroup-dl.jar			\
		$(JIVE_VOB)/lib/tools.jar					\
		$(REL_DIR_HARNESS)/$(LIB_DIR)

	cd $(QA_VOB);								\
	tar hcf - harness/trust | (cd $(REL_DIR_HARNESS) ; tar xvf -)
	chmod -R a+rw $(REL_DIR_HARNESS)/$(TRUST_DIR)

	cd $(QA_VOB)/src;	                                                \
	tar hcf - doc      | (cd $(REL_DIR_HARNESS) ; tar xvf -)

	cd $(REL_TOP);								\
	$(ZIP) -q -n .class -r $(KIT_QA)/$(RELEASE_JINIQA_HARNESS.zip) $(PRODNAME_HARNESS)
##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

tests:
	mkdir -p $(REL_DIR_TESTS)/$(LIB_DIR)		\
		 $(REL_DIR_TESTS)/$(TEST_RESOURCES_DIR) \
		 $(REL_DIR_TESTS)/$(E2E_RESOURCES_DIR)	

	cp -p 	$(QA_VOB)/lib/qa1*.jar						\
		$(QA_VOB)/lib/jinitests.jar					\
		$(QA_VOB)/lib/fullloggingfilter.jar				\
		$(REL_DIR_TESTS)/$(LIB_DIR)

	cd $(QA_VOB)/src;	                                                \
	sed -e 's|^com.sun.jini.jsk.home=.*|com.sun.jini.jsk.home=/files/$(JINI_PRODNAME)|'		\
	    -e 's|^com.sun.jini.qa.home=.*|com.sun.jini.qa.home=/files/$(PRODNAME_HARNESS)|'		\
	    -e 's|^com.sun.jini.test.home=.*|com.sun.jini.test.home=/files/$(PRODNAME_TESTS)|'		\
		$(QAHARNESS_PROP_FILE)						\
		> $(REL_DIR_TESTS)/$(QAHARNESS_PROP_FILE);			\
	cp $(E2E_RESOURCES_DIR)/* $(REL_DIR_TESTS)/$(E2E_RESOURCES_DIR)

	cd $(REL_TOP);								\
	$(ZIP) -q -n .class -r $(KIT_QA)/$(RELEASE_JINIQA_TESTS.zip) $(PRODNAME_TESTS)


##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

jtregtests:
	mkdir -p $(REL_DIR_JTREG)

	cd $(QA_VOB);								\
	tar hcf - jtreg | (cd $(REL_DIR_JTREG) ; tar xvf -)
	chmod -R a+rw $(REL_DIR_JTREG)

	cd $(REL_TOP);								\
	$(ZIP) -q -n .class -r $(KIT_QA)/$(RELEASE_JINIQA_JTREG.zip) $(PRODNAME_JTREG)
##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


jiniqa_har:
	rm -rf	 $(REL_DIR_HAR)							\
		 $(KIT_QA)/$(RELEASE_JINIQA_HAR.zip)

	mkdir -p $(REL_DIR_HAR)/doc/license					\
		 $(REL_DIR_HAR)/lib						\
		 $(KIT_QA)

	cd $(QA_VOB);								\
	cp -p	doc/license/qatest.txt $(REL_DIR_HAR)/doc/license
	chmod +w $(REL_DIR_HAR)/doc/license/qatest.txt

	cd $(QA_VOB)/src;							\
	cp -p com/sun/jini/qa/resources/qaHarness.prop $(REL_DIR_HAR)

	cd $(QA_VOB)/src;							\
	find	GNUmakefile							\
		GNUmakefile.allinc						\
		doc/api/							\
		com/sun/jini/qa/harness/					\
		com/sun/jini/qa/resources/qaDefaults.properties			\
		-depth -print							\
		| $(CPIO) $(REL_DIR_HAR)

	cp -p $(QA_VOB)/lib/harness.jar $(REL_DIR_HAR)/lib

	cd $(REL_TOP);								\
	$(ZIP) -q -n .class -r $(KIT_QA)/$(RELEASE_JINIQA_HAR.zip) $(PRODNAME_HAR)

	cd $(KIT_QA);								\
	echo "\n\nAttachment A: File Listing\n\n"				\
	>> $(REL_DIR_HAR)/doc/license/qatest.txt;				\
	$(UNZIP_LIST) $(KIT_QA)/$(RELEASE_JINIQA_HAR.zip)		      |	\
	fgrep "$(PRODNAME_HAR)"						      |	\
	fgrep -v ".zip"							      |	\
	awk '{print "\t" $$NF}'						      |	\
	egrep -v '/$$'								\
	>> $(REL_DIR_HAR)/doc/license/qatest.txt

	cd $(REL_TOP);								\
	$(ZIP) -q -n .class -r $(KIT_QA)/$(RELEASE_JINIQA_HAR.zip)		\
		$(PRODNAME_HAR)/doc/license/qatest.txt


##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

jiniqa_src_only: kit.clean kit.setup jiniqa_src

jiniqa_src: 
	rm -rf	 $(REL_DIR_SRC)							\
		 $(KIT_QA)/$(RELEASE_JINIQA_SRC.zip)

	mkdir -p $(REL_DIR_SRC)/doc/license					\
		 $(KIT_QA)

	cd $(QA_VOB);								\
	cp -p	doc/license/qatest.txt $(REL_DIR_SRC)/doc/license
	chmod +w $(REL_DIR_SRC)/doc/license/qatest.txt

	cd $(VOB);								\
	find    qa/harness qa/src 	 			\
		tools/src/com/sun/jini/tool/ClassServer.java 			\
                 -depth -print                                                  \
                | fgrep -v 'qa/src/doc'                                      	\
                | fgrep -v 'qa/src/overview.html'                               \
                | fgrep -v 'qa/src/testsuite.html'                              \
                | fgrep -v 'qa/src/com/sun/jini/qaman'                          \
                | fgrep -v 'qa/src/com/sun/jini/index.html'                     \
                | fgrep -v 'qa/src/com/sun/jini/qa/doc'                         \
                | fgrep -v 'qa/src/com/sun/jini/qa/examples'                    \
                | fgrep -v 'qa/src/com/sun/jini/qa/admin'                       \
                | fgrep -v 'qa/tests/examples'                           	\
                | fgrep -v 'qa/tests/jtreg'                           		\
		| $(CPIO) $(REL_DIR_SRC)


	cd $(REL_TOP);								\
	$(ZIP) -q -n .class -r $(KIT_QA)/$(RELEASE_JINIQA_SRC.zip) $(PRODNAME_SRC)

	cd $(KIT_QA);								\
	echo "\n\nAttachment A: File Listing\n\n"				\
	>> $(REL_DIR_SRC)/doc/license/qatest.txt;				\
	$(UNZIP_LIST) $(KIT_QA)/$(RELEASE_JINIQA_SRC.zip)		      |	\
	fgrep "$(PRODNAME_SRC)"						      |	\
	fgrep -v ".zip"							      |	\
	awk '{print "\t" $$NF}'						      |	\
	egrep -v '/$$'								\
	>> $(REL_DIR_SRC)/doc/license/qatest.txt

	cd $(REL_TOP);								\
	$(ZIP) -q -n .class -r $(KIT_QA)/$(RELEASE_JINIQA_SRC.zip)		\
		$(PRODNAME_SRC)/doc/license/qatest.txt


##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

checksum::
	$(CHECKSUM) $(KIT_QA)/$(RELEASE_JINIQA.zip) > $(KIT_QA)/$(RELEASE_JINIQA_CHKSUM)

# {{ Generate build_info.txt

build.info:
	STAMP=`/bin/date '+%d-%h-%Y.%H:%M'`;						\
	echo ""					>  $(KIT_QA)/$(BUILD_INFO.txt);	\
	echo "##"				>> $(KIT_QA)/$(BUILD_INFO.txt);	\
	echo "## Build Completed: $$STAMP"	>> $(KIT_QA)/$(BUILD_INFO.txt);	\
	echo "##"				>> $(KIT_QA)/$(BUILD_INFO.txt);	\
	echo ""					>> $(KIT_QA)/$(BUILD_INFO.txt)

	-cleartool catcs | egrep -v '^#' >> $(KIT_QA)/$(BUILD_INFO.txt)

	cp -p $(KIT_QA)/$(BUILD_INFO.txt) $(REL_DIR)/$(DOC_DIR)

	cd $(REL_TOP);									\
	$(ZIP) $(KIT_QA)/$(RELEASE_JINIQA.zip) $(PRODNAME)/$(DOC_DIR)/$(BUILD_INFO.txt)

	rm -f  $(REL_DIR)/$(DOC_DIR)/$(BUILD_INFO.txt)
# }}


# {{ Generate the product-info file:

product.info:
	##
	## Generate the product-info file:
	##

	rm -f $(KIT_QA)/$(PRODUCT_INFO_FILE)

	echo ""					>  $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "## full harness/tests release kit">> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jiniqa_file=$(RELEASE_JINIQA.zip)">> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jiniqa_dir=$(PRODNAME)"		>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jiniqa_ver=$(VERSION)"		>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo ""					>> $(KIT_QA)/$(PRODUCT_INFO_FILE)

	@echo ""
	echo ""					>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "## test binary kit"		>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jinitests_file=$(RELEASE_JINIQA_TESTS.zip)">> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jinitests_dir=$(PRODNAME_TESTS)"	>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jinitests_ver=$(VERSION)"		>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo ""					>> $(KIT_QA)/$(PRODUCT_INFO_FILE)

	@echo ""
	@echo ""
	echo ""					>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "## harness binary kit"		>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jiniharness_file=$(RELEASE_JINIQA_HARNESS.zip)">> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jiniharness_dir=$(PRODNAME_HARNESS)">> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jiniharness_ver=$(VERSION)"	>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo ""					>> $(KIT_QA)/$(PRODUCT_INFO_FILE)

	@echo ""
	@echo ""
	echo ""					>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "## jtreg kit"			>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jinijtreg_file=$(RELEASE_JINIQA_JTREG.zip)">> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jinijtreg_dir=$(PRODNAME_JTREG)">> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo "jinijtreg_ver=$(VERSION)"	>> $(KIT_QA)/$(PRODUCT_INFO_FILE)
	echo ""					>> $(KIT_QA)/$(PRODUCT_INFO_FILE)

	@echo ""
# }}


kit.setup::
	mkdir -p $(KIT_QA)
	
kit.clean clean::
	-rm -rf $(KIT_QA)
	-rm -rf $(REL_DIR)
	-rm -rf $(REL_DIR_HAR)
	-rm -rf $(REL_DIR_HARNESS)
	-rm -rf $(REL_DIR_TESTS)
	-rm -rf $(REL_DIR_SRC)
	-rm -rf $(REL_DIR_JTREG)

qa.doc::
	cd $(QA_VOB)/src
	rm -rf doc
	mkdir doc
	mkdir doc/api

	$(JAVADOC) $(JAVADOC_ARGS)						\
		com.sun.jini.qa.harness

