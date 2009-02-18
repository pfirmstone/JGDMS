#! /bin/sh -f
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

set -ex

. ./krb-setenv.sh

java -Djava.security.krb5.realm=$REALM \
     -Djava.security.krb5.kdc=$KDC_HOST \
     sun.security.krb5.internal.tools.Kinit \
     -f -k -t config/jgssTests.keytab \
     -c FILE:config/testClient1.tgt testClient1

java -Djava.security.krb5.realm=$REALM \
     -Djava.security.krb5.kdc=$KDC_HOST \
     sun.security.krb5.internal.tools.Kinit \
     -f -k -t config/jgssTests.keytab \
     -c FILE:config/testClient2.tgt testClient2

java -Djava.security.krb5.realm=$REALM \
     -Djava.security.krb5.kdc=$KDC_HOST \
     sun.security.krb5.internal.tools.Kinit \
     -f -k -t config/jgssTests.keytab \
     -c FILE:config/testClient3.tgt testClient3
