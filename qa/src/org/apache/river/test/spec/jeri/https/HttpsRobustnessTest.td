testClass=org.apache.river.test.spec.jeri.transport.RobustnessTest
testCategories=jeri,jeri_spec
testConfiguration=<url: HttpsServerEndpointTest.config>
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
#testjvmargs=\
#-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}
#-Djava.security.debug=access:failure,\
#-Dnet.jini.security.policy.PolicyFileProvider.basePolicyClass=net.jini.security.policy.ConcurrentPolicyFile,\
#-Djava.security.manager=org.apache.river.tool.ProfilingSecurityManager,\
#${testjvmargs}
org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.qa.harness.SkipConfigTestVerifier
org.apache.river.qa.harness.skipConfigs=none,jeri,jrmp,kerberos,http