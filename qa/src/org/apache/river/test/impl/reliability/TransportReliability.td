testClass=AppleUserImpl
testCategories=reliability
testConfiguration=<url:configs/<config>/TransportReliability.config>
testConfigurationOptional=true
testPolicyFile=<url:harness/policy/all.policy>

# disable all class servers
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
org.apache.river.qa.testClassServer=

# skip jrmp because this type of reliability testing is
#           already done by the J2SE RMI reliability test
# skip none because this is just a duplication of jeri
# skip kerberos because dgc is required by the test
#           but is not supported by the kerberos provider
org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.qa.harness.SkipConfigTestVerifier
org.apache.river.qa.harness.skipConfigs=none,jrmp,kerberos

org.apache.river.test.impl.reliability.maxLevel=8
org.apache.river.test.impl.reliability.maxThreads=12
