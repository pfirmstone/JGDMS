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
# This file should be sourced instead of running directly

# Shell script to be sourced to set environment variables used by
# Kerberos scripts.  You should modify the variable values to match
# your environment setup.

# Client principal, excluding the realm
# Example: CLIENT=client
CLIENT=${CLIENT:-client}

# Server principal, including server name and instance, but not the realm
# Example: SERVER=server or
#          SERVER=server/server1.xyz.com
SERVER=${SERVER:-server}

# Phoenix principal, including phoenix name and instance, but not the realm
# Example: PHOENIX=phoenix or
#          PHOENIX=phoenix/server2.xyz.com
PHOENIX=${PHOENIX:-phoenix}

# Reggie principal, including reggie name and instance, but not the realm
# Example: REGGIE=reggie or
#          REGGIE=reggie/server2.xyz.com
REGGIE=${REGGIE:-reggie}

# Default realm used by KDC and all principals in this example
# Example: REALM=REALM1.XYZ.COM
REALM=${REALM:-your_default_realm}

# Host on which the KDC server is running
# Example: KDC_HOST=server3.xyz.com
KDC_HOST=${KDC_HOST:-your_kdc_host}
