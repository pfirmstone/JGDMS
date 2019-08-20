testClass=RestoreContextForTasks
testCategories=lookupdiscovery,lookupdiscovery_impl,impllookupdiscovery
testPolicyfile=<url: restoretest.policy>
testConfiguration=<url: rc.config>
testConfigurationOptional=false
include0=lookupdiscovery.properties
include1=org/apache/river/test/share/lookups1_0.properties

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.qa.harness.SkipConfigTestVerifier
org.apache.river.qa.harness.skipConfigs=none,jrmp,kerberos,http,https
