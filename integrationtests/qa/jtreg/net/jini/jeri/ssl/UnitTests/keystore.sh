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

${KEYSTORECMD} -keyclone -alias clientDSA2 -dest clientDSA2expired -new keypass
${KEYSTORECMD} -selfcert -alias clientDSA2expired

${KEYSTORECMD} -genkey -alias serverRSA2 -dname CN=serverRSA2 -keyalg RSA
${KEYSTORECMD} -certreq -alias serverRSA2 -file serverRSA2.request

${KEYSTORECMD} -keyclone -alias serverRSA2 -dest serverRSA2expired -new keypass
${KEYSTORECMD} -selfcert -alias serverRSA2expired

set +x

echo Sign clientDSA2.req and serverRSA2.req and then import them:
echo ${TRUSTSTORECMD} -import -noprompt -alias ca -file ca.cert
echo ${KEYSTORECMD} -import -noprompt -alias ca -file ca.cert
echo ${KEYSTORECMD} -import -noprompt -alias clientDSA2 -file clientDSA2.cert
echo ${KEYSTORECMD} -import -noprompt -alias clientDSA2expired -file clientDSA2expired.cert
echo ${KEYSTORECMD} -import -noprompt -alias serverRSA2 -file serverRSA2.cert
echo ${KEYSTORECMD} -import -noprompt -alias serverRSA2expired -file serverRSA2expired.cert
