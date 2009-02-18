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
# Run java standalone, so we can specify system properties before they
# are used.

if [ ! "${TESTJAVA}" ]; then
    echo Error: TESTJAVA environment variable is not set
    exit 1;
fi

if [ ! "${TESTCLASSES}" ]; then
    echo Error: TESTCLASSES environment variable is not set
    exit 1;
fi

if [ ! "${TESTSRC}" ]; then
    echo Error: TESTSRC environment variable is not set
    exit 1;
fi

#
# Set platform-dependent variables
#
OS=`uname -s`
case "$OS" in
  SunOS )
    FS="/"
    ;;
  Linux )
    FS="/"
    ;;
  Windows_95 | Windows_98 | Windows_NT )
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac


${TESTJAVA}${FS}bin${FS}java -Djava.security.properties=${TESTSRC}${FS}security.properties -Dtest.src=${TESTSRC} -cp ${TESTCLASSES} $*
