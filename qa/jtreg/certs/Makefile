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
#DSTCROOT =	/home/tjb/lib/jcsi/jcsi_v1.0b1 #replaced with BC_LIB
#JDK13HOME =	/files/jdk13 #replaced with JAVA_HOME

BC_LIB = ../../../dep-libs/bouncy-castle

KEYTOOL=${JAVA_HOME}/bin/keytool
KEYSTORECMD=${KEYTOOL} -storepass keypass -keypass keypass -validity 3650

JINIGROUP=OU=Jini Group, O=Sun Microsystems Inc, L=Burlington, ST=MA, C=US

CA_NAMES=1 2
USER_NAMES=A B C D E

#compile:
#	${JAVA_HOME}/bin/javac \
#	  -d . \
#	  -classpath .:${DSTCROOT}/classes:${DSTCROOT}/jars/jcsi.jar \
#	  *.java

compile:
	${JAVA_HOME}/../bin/javac \
	  -d . \
	  -classpath .:${BC_LIB}/bcprov-jdk15on-154.jar:${BC_LIB}/bcpkix-jdk15on-154.jar:${BC_LIB}/bcmail-jdk15on-154.jar \
	  *.java

create:
	rm -f keystores/*.*store
	rm -rf requests
	mkdir requests
	for c in ${CA_NAMES}; do \
	    for u in ${USER_NAMES}; do \
		echo Creating Test User $$c$$u; \
		${KEYSTORECMD} \
		    -genkey \
		    -keystore keystores/test-user$$c$$u.keystore \
		    -alias test-user$$c$$u \
		    -dname "CN=Test User $$c$$u, ${JINIGROUP}"; \
		${KEYSTORECMD} \
		    -certreq \
		    -keystore keystores/test-user$$c$$u.keystore \
		    -alias test-user$$c$$u \
		    -file requests/test-user$$c$$u.request; \
	    done; \
	done

create_users_truststore:
	rm -f keystores/users.truststore
	for c in ${CA_NAMES}; do \
	    for u in ${USER_NAMES}; do \
	    	${KEYSTORECMD} \
		    -export \
		    -keystore keystores/test-user$$c$$u.keystore \
		    -alias test-user$$c$$u -file /tmp/tmp.cert; \
		${KEYSTORECMD} \
		    -import \
		    -keystore keystores/users.truststore \
		    -alias test-user$$c$$u -noprompt -file /tmp/tmp.cert; \
	    done; \
	done
	rm -f /tmp/tmp.cert

import:
	for c in ${CA_NAMES}; do \
	    ${KEYSTORECMD} \
		-import \
		-noprompt \
		-keystore keystores/test-ca$$c.truststore \
		-alias test-ca$$c \
		-file keys/test-ca$$c.cert; \
	    ${KEYSTORECMD} \
		-import \
		-noprompt \
		-keystore keystores/test-ca.truststore \
		-alias test-ca$$c \
		-file keys/test-ca$$c.cert; \
	    for u in ${USER_NAMES}; do \
		${KEYSTORECMD} \
		    -import \
		    -noprompt \
		    -keystore keystores/test-user$$c$$u.keystore \
		    -alias test-user$$c$$u \
		    -file requests/test-user$$c$$u.chain; \
	    done; \
	done

clean:
	rm *.class
	rm -rf requests
