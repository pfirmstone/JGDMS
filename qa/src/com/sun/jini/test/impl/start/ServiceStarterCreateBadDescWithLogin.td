testClass=ServiceStarterCreateBadDesc
testCategories=start,start_impl

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.qa.harness.SkipConfigTestVerifier
com.sun.jini.qa.harness.skipConfigs=none,jrmp,http,jeri,https,kerberos

test.bogusConfig=<url:ServiceStarterCreateBadDescWithLogin.config>

include0=start.properties
