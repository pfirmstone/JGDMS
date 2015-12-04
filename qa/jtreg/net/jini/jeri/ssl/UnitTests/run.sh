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
# Convenience script for running a test in this directory as a
# standalone test, passing standard argument via environment variables.
# Usage: run.sh [args] [classname]

#
# Set test environment defaults for use outside of jtreg.
#
if [ ! "${TESTJAVA}" ]; then
    echo Error: TESTJAVA environment variable is not set
    exit 1;
fi
if [ ! "${TESTSRC}" ]; then
    TESTSRC=".";
fi

# Additional extension directory, to get JSSE with strong encryption
if [ "${EXTDIR}" ]; then
    TESTARGS="${TESTARGS} -Dextdir=${EXTDIR}"
    TESTARGS="${TESTARGS} -Djava.ext.dirs=${EXTDIR}${PS}${TESTJAVA}/lib/ext${PS}${TESTJAVA}/jre/lib/ext";
    COMPILEARGS="-extdirs=${EXTDIR}${PS}${TESTJAVA}/lib/ext${PS}${TESTJAVA}/jre/lib/ext";
fi

# Additional initial boot classpath, to get RMI security stuff
if [ "${BOOTCP}" ]; then
    TESTARGS="${TESTARGS} -Xbootclasspath/p:${BOOTCP}";
    COMPILEARGS="-bootclasspath ${BOOTCP}${PC}${TESTJAVA}/lib/rt.jar${PC}${TESTJAVA}/jre/lib/rt.jar"
fi

# Specify the location of class files
if [ ! "${TESTCLASSES}" ]; then
    TESTCLASSES="classes";
fi

# Compile unless told not to
if [ ! "${TESTNOCOMPILE}" ]; then
    echo
    echo '***' Compiling: `date`
    echo
    if [ ! -d classes ]; then
	mkdir classes;
    fi
    echo ${TESTJAVA}/bin/javac ${COMPILEARGS} -d ${TESTCLASSES} *.java;
    ${TESTJAVA}/bin/javac ${COMPILEARGS} -d ${TESTCLASSES} *.java;
    if [ $? -ne 0 ]; then
	echo Compilation failed
	exit 1;
    fi
fi

#
# Set platform-dependent variables
#
OS=`uname -s`
case "$OS" in
  SunOS )
    PS=":"
    FS="/"
    ;;
  Linux )
    PS=":"
    FS="/"
    ;;
  Windows_95 | Windows_98 | Windows_NT )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

#
# Environment variables that can be set to provide further control over
# the test.
#

# Print Java security debugging
if [ "${DBGSEC}" = "true" ]; then
    DBGSEC="access,failure";
fi
if [ "${DBGSEC}" ]; then
    TESTARGS="${TESTARGS} -Djava.security.debug=${DBGSEC}";
fi

# Print JSSE debugging output
if [ "${DBGJSSE}" = "true" ]; then
    DBGJSSE="ssl,handshake,session";
fi
if [ "${DBGJSSE}" ]; then
    TESTARGS="${TESTARGS} -Djavax.net.debug=${DBGJSSE}";
fi

# Print server debugging messages for the provider
if [ "${DBGSERVER}" ]; then
    TESTARGS="${TESTARGS} -Dorg.apache.river.temp.davis.securejeri.jsse.serverLog=true";
fi

# Print client debugging messages for the provider
if [ "${DBGCLIENT}" ]; then
    TESTARGS="${TESTARGS} -Dorg.apache.river.temp.davis.securejeri.jsse.clientLog=true";
fi

# Print RMI debugging messages
if [ "${DBGRMI}" ]; then
    TESTARGS="${TESTARGS} -Djava.rmi.server.logCalls=true"
    TESTARGS="${TESTARGS} -Dsun.rmi.server.logLevel=20"
    TESTARGS="${TESTARGS} -Dsun.rmi.client.logCalls=true"
    TESTARGS="${TESTARGS} -Dsun.rmi.security.debug=true"
    TESTARGS="${TESTARGS} -Dsun.rmi.loader.logLevel=verbose";
fi

# Control test level
if [ "${TESTLEVEL}" ]; then
    TESTARGS="${TESTARGS} -DtestLevel=${TESTLEVEL}";
fi

# The first test run
if [ "${FIRSTTEST}" ]; then
    TESTARGS="${TESTARGS} -DfirstTest=${FIRSTTEST}";
fi

# The last test run
if [ "${LASTTEST}" ]; then
    TESTARGS="${TESTARGS} -DlastTest=${LASTTEST}";
fi

# Stop if test failure occurs
if [ "${STOPONFAIL}" ]; then
    TESTARGS="${TESTARGS} -DstopOnFail=true";
fi

# Specify the initial seed for random tests
if [ "${SEED}" ]; then
    TESTARGS="${TESTARGS} -Dseed=${SEED}";
fi

# Whether to use HTTPS for tests that know how to
if [ "${USEHTTPS}" ]; then
    TESTARGS="${TESTARGS} -DuseHttps=true";
fi

# Additional, standard arguments
TESTARGS="$TESTARGS -Djava.security.manager"
TESTARGS="$TESTARGS -Djava.security.policy=$TESTSRC/policy"
TESTARGS="$TESTARGS -Dtest.src=$TESTSRC"
TESTARGS="$TESTARGS -Dtest.classes=$TESTCLASSES"
TESTARGS="$TESTARGS -Dorg.apache.river.temp.davis.jeri.server.hostname=localhost"
TESTARGS="$TESTARGS -cp $TESTCLASSES"

# Use quantify
if [ "${QUANTIFY}" ]; then
    QUANTIFY=qstart;
else
    QUANTIFY=;
fi

# Specify the test class
if [ "$1" ]; then
    TESTARGS="${TESTARGS} $*";
else
    TESTARGS="${TESTARGS} TestAll";
fi

# Don't run test if TESTNORUN is set
if [ ! "${TESTNORUN}" ]; then

    #
    # Echo the start time and command line
    #
    echo
    echo '***' Start test VM: `date`
    echo
    echo $QUANTIFY $TESTJAVA/bin/java $TESTARGS

    #
    # Run the test
    #
    $QUANTIFY $TESTJAVA/bin/java $TESTARGS
fi
