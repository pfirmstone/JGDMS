testClass=ServiceStarterCreateBadDesc
testCategories=start,start_impl

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.qa.harness.SkipConfigTestVerifier
org.apache.river.qa.harness.skipConfigs=none,jrmp,http,jeri,https,kerberos

test.bogusConfig=<url:ServiceStarterCreateBadDescWithLogin.config>

include0=start.properties
