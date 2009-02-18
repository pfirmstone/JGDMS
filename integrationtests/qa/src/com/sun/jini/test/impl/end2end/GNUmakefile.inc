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
JAVA.bin	= $(JAVA.home)/bin
KEYTOOL		= $(JAVA.bin)/keytool
RESOURCEDIR	= $(SRC)/com/sun/jini/test/impl/end2end/resources
KEYPASS		= $(RESOURCEDIR)/keystore.password
KEYSTORE	= $(RESOURCEDIR)/keystore
policy_tmpfile	= /tmp/.policygen

# Keystore aliases and distinguished names
CLIENTDSA1_ALIAS  = clientDSA1
CLIENTDSA1_DN     = CN=clientDSA1
CLIENTDSA2_ALIAS  = clientDSA2
CLIENTDSA2_DN     = CN=clientDSA2
CLIENTRSA1_ALIAS = clientRSA1
CLIENTRSA1_DN    = CN=clientRSA1
CLIENTRSA2_ALIAS = clientRSA2
CLIENTRSA2_DN    = CN=clientRSA2
SERVERDSA_ALIAS  = serverDSA
SERVERDSA_DN     = CN=serverDSA
SERVERRSA_ALIAS = serverRSA
SERVERRSA_DN    = CN=serverRSA
kerberosClient = qa-e2e-client@DAVIS.JINI.SUN.COM
kerberosServer = qa-e2e-server@DAVIS.JINI.SUN.COM

end2end: generate-generators generate-interface generate-policy

generate-generators:
	rm -rf $(CLASSDIR)/com/sun/jini/test/impl/end2end/generators
	$(JAVAC.exec) $(SRC)/com/sun/jini/test/impl/end2end/generators/*.java
    
generate-interface:
	$(JAVA) -cp $(CLASSDIR) \
	com.sun.jini.test.impl.end2end.generators.InterfaceGenerator \
	> $(SRC)/com/sun/jini/test/impl/end2end/e2etest/ConstraintsInterface.java

generate-policy:
	$(JAVA) -cp $(CLASSDIR) \
	com.sun.jini.test.impl.end2end.generators.SecurityGenerator \
	> $(policy_tmpfile)
	cat $(SRC)/com/sun/jini/test/impl/end2end/policies/policy.server.frontend $(policy_tmpfile) \
	> $(TOP)/policy/policy.end2end
	rm -rf $(policy_tmpfile)

keystore-password:
	mkdir -p $(RESOURCEDIR)
	rm -f $(KEYPASS)
	echo keypass > $(KEYPASS)

create-keystore:
	mkdir -p $(RESOURCEDIR)
	rm -f $(KEYSTORE)
	$(KEYTOOL) -genkey \
		-alias $(CLIENTDSA1_ALIAS) \
		-dname "$(CLIENTDSA1_DN)" \
		-keyalg DSA \
		-keypass keypass \
		-keystore $(KEYSTORE) \
		-validity 1460 \
		-storepass keypass
	$(KEYTOOL) -genkey \
		-alias $(CLIENTDSA2_ALIAS) \
		-dname "$(CLIENTDSA2_DN)" \
		-keyalg DSA \
		-keypass keypass \
		-keystore $(KEYSTORE) \
		-validity 1460 \
		-storepass keypass
	$(KEYTOOL) -genkey \
		-alias $(CLIENTRSA1_ALIAS) \
		-dname "$(CLIENTRSA1_DN)" \
		-keyalg RSA \
		-keypass keypass \
		-keystore $(KEYSTORE) \
		-validity 1460 \
		-storepass keypass
	$(KEYTOOL) -genkey \
		-alias $(CLIENTRSA2_ALIAS) \
		-dname "$(CLIENTRSA2_DN)" \
		-keyalg RSA \
		-keypass keypass \
		-keystore $(KEYSTORE) \
		-validity 1460 \
		-storepass keypass
	$(KEYTOOL) -genkey \
		-alias $(SERVERDSA_ALIAS) \
		-dname "$(SERVERDSA_DN)" \
		-keyalg DSA \
		-keypass keypass \
		-keystore $(KEYSTORE) \
		-validity 1460 \
		-storepass keypass
	$(KEYTOOL) -genkey \
		-alias $(SERVERRSA_ALIAS) \
		-dname "$(SERVERRSA_DN)" \
		-keyalg RSA \
		-keypass keypass \
		-keystore $(KEYSTORE) \
		-validity 1460 \
