testClass=MulticastRequestDelay
testCategories=lookupdiscovery,lookupdiscovery_impl,impllookupdiscovery
testPolicyfile=<url:harness/policy/all.policy>
testConfiguration=<url:com/sun/jini/test/impl/lookupdiscovery/mrd.config>
testConfigurationOptional=true
com.sun.jini.qa.harness.runkitserver=false
com.sun.jini.qa.harness.runjiniserver=false

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.qa.harness.SkipConfigTestVerifier
com.sun.jini.qa.harness.skipConfigs=none,jrmp,kerberos,jsse,http,https
