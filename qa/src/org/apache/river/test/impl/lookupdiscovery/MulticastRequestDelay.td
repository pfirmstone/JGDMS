testClass=MulticastRequestDelay
testCategories=lookupdiscovery,lookupdiscovery_impl,impllookupdiscovery
testPolicyfile=<url:harness/policy/all.policy>
testConfiguration=<url:org/apache/river/test/impl/lookupdiscovery/mrd.config>
testConfigurationOptional=true
org.apache.river.qa.harness.runkitserver=false
org.apache.river.qa.harness.runjiniserver=false

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.qa.harness.SkipConfigTestVerifier
org.apache.river.qa.harness.skipConfigs=none,jrmp,kerberos,jsse,http,https
