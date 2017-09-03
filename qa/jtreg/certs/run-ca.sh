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
# Run a DSTC certificate authority, specifying the properties file as
# the  argument.
# Example:
#run-ca.sh -CA test-ca2.properties
#
# Directory containing classes that patch JCSI
#PATCHROOT=/home/tjb/.jcsi
# JSCI classes
#DSTCROOT=/home/tjb/lib/jcsi/jcsi_v1.0b1

# JCSI has been replaced with Bouncy Castle
BC_LIB=lib/bouncy-castle
JTREG_DIR=${RIVER_HOME}/qa/jtreg

# JCSI uses a different format for requesting Cipher algorithms than is
# supported by the JDK 1.4, so use 1.3
#JDK13HOME=/files/jdk13

#$JDK13HOME/bin/java -cp .:$PATCHROOT:$DSTCROOT/classes:$DSTCROOT/jars/jcsi.jar \
#     -Djcsi.ca.conf=$1 CA

# Changed, so the first argument is the option to pass the CA, the second is the configuration file.
"$JAVA_HOME/bin/java" -cp .:${BC_LIB}/bcprov-jdk15on-154.jar:${BC_LIB}/bcpkix-jdk15on-154.jar:${BC_LIB}/bcmail-jdk15on-154.jar:${JTREG_DIR}/certs \
      -Djtreg.dir=${JTREG_DIR} -Djcsi.ca.conf=$2 CA $1
