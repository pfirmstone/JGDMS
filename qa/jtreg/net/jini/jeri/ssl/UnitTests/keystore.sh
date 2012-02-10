#! /bin/sh
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
# Create the keystore and truststore files
# Usage: keystore.sh
#
# You must first compile CA.java in the qa/jtreg/certs directory by calling
# make compile, in that directory.  When you've finished, run this script, 
# to generate new certificates. 
# This task needs to be performed once every ten years when certificates expire.

if [ "${TESTJAVA}" ]; then
    JAVABIN=${TESTJAVA}/bin/;
else
    JAVABIN=;
fi
if [ ! "${TESTSRC}" ]; then
    TESTSRC=".";
fi

KEYSTORE=${TESTSRC}/keystore
TRUSTSTORE=${TESTSRC}/truststore

KEYTOOL=${JAVABIN}keytool

KEYSTORECMDEXP="${KEYTOOL} -keystore ${KEYSTORE} -storepass keypass -keypass keypass -validity 1"
KEYSTORECMD="${KEYTOOL} -keystore ${KEYSTORE} -storepass keypass -keypass keypass -validity 3650"
TRUSTSTORECMD="${KEYTOOL} -keystore ${TRUSTSTORE} -storepass keypass -keypass keypass -validity 3650"

set -x

rm ${KEYSTORE}

${KEYSTORECMD} -genkey -alias clientDSA  -dname CN=clientDSA  -keyalg DSA
${KEYSTORECMD} -genkey -alias clientRSA1 -dname 'CN=clientRSA1, C=US' -keyalg RSA
${KEYSTORECMD} -genkey -alias clientRSA2 -dname CN=clientRSA2 -keyalg RSA
${KEYSTORECMD} -genkey -alias serverDSA  -dname 'CN=serverDSA, C=US'  -keyalg DSA
${KEYSTORECMD} -genkey -alias serverRSA  -dname CN=serverRSA  -keyalg RSA
${KEYSTORECMD} -genkey -alias noPerm     -dname CN=noPerm     -keyalg DSA

rm ${TRUSTSTORE}
cp ${KEYSTORE} ${TRUSTSTORE}

${KEYSTORECMD} -genkey -alias notTrusted -dname CN=notTrusted -keyalg RSA

# The following commands depend on using an outside certificate
# authority to sign each of the certificates and supply a certificate
# for the CA.

${KEYSTORECMD} -genkey -alias clientDSA2 -dname CN=clientDSA2 -keyalg DSA
${KEYSTORECMD} -certreq -alias clientDSA2 -file clientDSA2.request

${KEYSTORECMDEXP} -genkey -alias clientDSA2expired -dname CN=clientDSA2 -keyalg DSA
${KEYSTORECMDEXP} -certreq -alias clientDSA2expired -file clientDSA2expired.request

${KEYSTORECMD} -genkey -alias serverRSA2 -dname CN=serverRSA2 -keyalg RSA
${KEYSTORECMD} -certreq -alias serverRSA2 -file serverRSA2.request

${KEYSTORECMDEXP} -genkey -alias serverRSA2expired -dname CN=serverRSA2 -keyalg RSA
${KEYSTORECMDEXP} -certreq -alias serverRSA2expired -file serverRSA2expired.request

set +x
echo Sign clientDSA2.req, serverRSA2.req, clientDSA2expired.req and serverRSA2expired.req,\
 then import them:
echo expired certificates need one day to expire before testing.

set -x

../../../../../certs/run-ca.sh -CA ./ca.properties
../../../../../certs/run-ca.sh -CA ./ca1.properties
../../../../../certs/run-ca.sh -CR ./ca.properties
../../../../../certs/run-ca.sh -CR ./ca1.properties
../../../../../certs/run-ca.sh -CR ./serverRSA2expired.properties
../../../../../certs/run-ca.sh -CR ./clientDSA2expired.properties

${TRUSTSTORECMD} -import -noprompt -alias ca -file ca.cert
${TRUSTSTORECMD} -import -noprompt -alias ca1 -file ca1.cert
${KEYSTORECMD} -import -noprompt -alias ca -file ca.cert
${KEYSTORECMD} -import -noprompt -alias ca1 -file ca1.cert
${KEYSTORECMD} -import -noprompt -alias clientDSA2 -file clientDSA2.chain
${KEYSTORECMDEXP} -import -noprompt -alias clientDSA2expired -file clientDSA2expired.chain
${KEYSTORECMD} -import -noprompt -alias serverRSA2 -file serverRSA2.chain
${KEYSTORECMDEXP} -import -noprompt -alias serverRSA2expired -file serverRSA2expired.chain
