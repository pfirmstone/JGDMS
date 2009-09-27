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

# Shell script to run confirming SSL server

# The server host
host=`hostname`

set -x

java -Djava.security.manager= 						\
     -Djava.security.policy=config/ssl-server.policy			\
     -Djava.security.auth.login.config=config/ssl-server.login 		\
     -Djava.security.properties=config/dynamic-policy.security-properties \
     -Djavax.net.ssl.trustStore=prebuiltkeys/truststore 		\
     -Djava.protocol.handler.pkgs=net.jini.url				\
     -Djava.rmi.server.RMIClassLoaderSpi=com.sun.jini.example.hello.MdClassAnnotationProvider \
     -Dexport.codebase.source.app=lib 					\
     -Dexport.codebase.app=httpmd://$host:8080/server-dl.jar\;sha=0	\
     -Dexport.codebase.source.jsk=../../lib-dl 		\
     -Dexport.codebase.jsk=httpmd://$host:8080/jsk-dl.jar\;sha=0	\
     -jar lib/server.jar						\
     config/confirming-ssl-server.config
