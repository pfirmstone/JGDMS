testClass=AppleUserImpl
testCategories=reliability
testConfiguration=<url:configs/<config>/TransportReliability.config>
testConfigurationOptional=true
testPolicyFile=<url:harness/policy/all.policy>

# disable all class servers
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
com.sun.jini.qa.testClassServer=

# skip jrmp because this type of reliability testing is
#           already done by the J2SE RMI reliability test
# skip none because this is just a duplication of jeri
# skip kerberos because dgc is required by the test
#           but is not supported by the kerberos provider
com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.qa.harness.SkipConfigTestVerifier
com.sun.jini.qa.harness.skipConfigs=none,jrmp,kerberos

com.sun.jini.test.impl.reliability.maxLevel=8
com.sun.jini.test.impl.reliability.maxThreads=12
