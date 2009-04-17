testClass=UnicastDelay
testCategories=locatordiscovery,locatordiscovery_impl,impllocatordiscovery
testPolicyfile=<url:harness/policy/all.policy>
testConfiguration=<url:com/sun/jini/test/impl/locatordiscovery/ud.config>
testConfigurationOptional=true
com.sun.jini.qa.harness.runkitserver=false
com.sun.jini.qa.harness.runjiniserver=false

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.qa.harness.SkipConfigTestVerifier
com.sun.jini.qa.harness.skipConfigs=none,jrmp,kerberos,jsse,http,https
