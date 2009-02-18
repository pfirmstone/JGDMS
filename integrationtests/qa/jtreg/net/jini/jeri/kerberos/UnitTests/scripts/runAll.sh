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

if [ "$1" = -c ]
then
javac -classpath .:../../../../../unittestlib -d classes TestAll.java
fi

java -Djava.security.manager= \
     -Djava.security.policy=config/policy.all \
     -Djava.security.auth.login.config=config/testEndpoints.login \
     -Djava.security.krb5.realm=DAVIS.JINI.SUN.COM \
     -Djava.security.krb5.kdc=jiniautot.east.sun.com \
     -classpath classes TestAll
