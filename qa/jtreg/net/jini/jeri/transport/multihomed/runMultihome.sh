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
# @test
# @summary Test the KerberosEndpoint for multihome support
# @author Vinod Johnson
# @build AbstractSocketFactory Multihomed TestNameService TestNameServiceDescriptor Resolver
# @run shell runMultihome.sh

set -ex	

. ${TESTSRC}/krb-setenv.sh

SEPARATOR=":"

case `uname` in
    Windows* )
	SEPARATOR=";"
esac

mkdir config
cp ${TESTSRC}/../../kerberos/UnitTests/config/jgssTests.keytab config

HOSTNAME=`${TESTJAVA}/bin/java -classpath ${TESTCLASSES} Resolver`
HOSTADDR=`${TESTJAVA}/bin/java -classpath ${TESTCLASSES} Resolver ${HOSTNAME}`
KDCADDR=`${TESTJAVA}/bin/java -classpath ${TESTCLASSES} Resolver ${KDC_HOST}`

${TESTJAVA}/bin/java -Djava.security.manager= \
     -Djava.security.policy=${TESTSRC}/config/policy.kerbmultihome \
     -Djava.security.auth.login.config=${TESTSRC}/config/testEndpoints.login \
     -Djava.security.krb5.realm=$REALM \
     -Djava.security.krb5.kdc=$KDC_HOST \
     -Dcom.sun.jini.jtreg.kerberos.multihome.hostname=$HOSTNAME \
     -Dcom.sun.jini.jtreg.kerberos.multihome.hostaddr=$HOSTADDR \
     -Dcom.sun.jini.jtreg.kerberos.multihome.kdcaddr=$KDCADDR \
     -Dsun.net.spi.nameservice.provider.1=test,test \
     -DendpointType=kerberos \
     -DtrustProxy=true \
     -classpath ${TESTCLASSES}${SEPARATOR}${TESTSRC} \
     Multihomed
