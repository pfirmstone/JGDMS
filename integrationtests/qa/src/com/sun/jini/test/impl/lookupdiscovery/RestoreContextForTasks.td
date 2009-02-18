testClass=RestoreContextForTasks
testCategories=lookupdiscovery,lookupdiscovery_impl,impllookupdiscovery
testPolicyfile=<url: restoretest.policy>
testConfiguration=<url: rc.config>
testConfigurationOptional=false
include0=lookupdiscovery.properties
include1=com/sun/jini/test/share/lookups1_0.properties

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.qa.harness.SkipConfigTestVerifier
com.sun.jini.qa.harness.skipConfigs=none,jrmp,kerberos,jsse,http,https
