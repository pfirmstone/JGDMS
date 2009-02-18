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
# Create the keystore file
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
KEYTOOL="${JAVABIN}keytool -genkey -keystore ${KEYSTORE} -storepass keypass -keypass keypass -validity 3650"

rm ${KEYSTORE}

${KEYTOOL} -alias client -dname "CN=Client, OU=RMI Group, O=Sun Microsystems Inc, L=Burlington, ST=MA, C=US"
