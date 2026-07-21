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

if [ ! "${TESTJAVA}" ]; then
    echo Error: TESTJAVA environment variable is not set
    exit 1;
fi
if [ ! "${TESTSRC}" ]; then
    TESTSRC=".";
fi

KEYSTORE=${TESTSRC}/keystore
# -keyalg is required explicitly by modern keytool (the old implicit
# DSA default was removed); RSA/2048 added for bring-up on a current
# JDK. Not a WI-1..5 item: discovered as further pre-existing fixture
# rot (the checked-in keystore's certs had also expired: NotAfter
# 2010) while bringing accessControl up for real per SOW risk R3.
KEYTOOL="${TESTJAVA}/bin/keytool -genkey -keystore ${KEYSTORE} -storepass keypass -keypass keypass -validity 3650 -keyalg RSA -keysize 2048"

rm -f ${KEYSTORE}

${KEYTOOL} -alias client1 -dname "CN=Client1"
${KEYTOOL} -alias client2 -dname "CN=Client2"
# lowercase "server": AccessControl.java's trustCtx hardcodes
# new X500Principal("CN=server") (lowercase); X500Principal.equals()
# compares DER encodings byte-for-byte (case-sensitive), so "CN=Server"
# here would never match and Security.verifyObjectTrust(sys, ...) would
# always fail with "object is not trusted" -- a pre-existing mismatch,
# unrelated to phoenix-dl, invisible before the test could actually run.
${KEYTOOL} -alias server -dname "CN=server"
