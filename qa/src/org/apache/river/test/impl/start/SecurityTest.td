testClass=SecurityTest
testCategories=start,start_impl
#testClasspath=${org.apache.river.qa.home}$/lib$/harness.jar$:${org.apache.river.qa.home}$/lib$/qa1-start-tests.jar$:${org.apache.river.qa.home}$/lib$/$qajinidep$:${org.apache.river.jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>
#testjvmargs=\
#-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}

/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService 1
org.apache.river.test.impl.start.SecurityTest1.type=activatable
org.apache.river.test.impl.start.SecurityTest1.impl=org.apache.river.test.impl.start.TestServiceImpl
org.apache.river.test.impl.start.SecurityTest1.classpath=<file:lib/qa1-start-testservice1.jar>
org.apache.river.test.impl.start.SecurityTest1.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice1-dl.jar
org.apache.river.test.impl.start.SecurityTest1.policyfile=<url:securityTest.testservice1.policy>
org.apache.river.test.impl.start.SecurityTest1.log=none
org.apache.river.test.impl.start.SecurityTest1.serverjvmargs=-Dnet.jini.security.policy.DynamicPolicyProvider.basePolicyClass=net.jini.security.policy.PolicyFileProvider
org.apache.river.test.impl.start.SecurityTest1.serviceConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.SecurityTest1.starterConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.SecurityTest1.host=master

// TestService 2 - same as above except different classpath, codebase, and policy file
org.apache.river.test.impl.start.SecurityTest2.type=activatable
org.apache.river.test.impl.start.SecurityTest2.impl=${org.apache.river.test.impl.start.SecurityTest1.impl}
org.apache.river.test.impl.start.SecurityTest2.classpath=<file:lib/qa1-start-testservice2.jar>
org.apache.river.test.impl.start.SecurityTest2.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice2-dl.jar
org.apache.river.test.impl.start.SecurityTest2.policyfile=<url:securityTest.testservice2.policy>
org.apache.river.test.impl.start.SecurityTest2.log=${org.apache.river.test.impl.start.SecurityTest1.log}
org.apache.river.test.impl.start.SecurityTest2.serverjvmargs=${org.apache.river.test.impl.start.SecurityTest1.serverjvmargs}
org.apache.river.test.impl.start.SecurityTest2.serviceConfiguration=${org.apache.river.test.impl.start.SecurityTest1.serviceConfiguration}
org.apache.river.test.impl.start.SecurityTest2.starterConfiguration=${org.apache.river.test.impl.start.SecurityTest1.starterConfiguration}
org.apache.river.test.impl.start.SecurityTest2.host=master

// Shared group overrides
sharedGroup.policyfile=<url:harness/policy/all.policy>
#sharedGroupImpl.policyfile=<url:harness/policy/policy.SecurityTest.sharedgroup>
include0=start.properties
