testClass=ClasspathTest
testCategories=start,start_impl
#testClasspath=${org.apache.river.qa.home}$/lib$/harness.jar$:${org.apache.river.qa.home}$/lib$/qa1-start-tests.jar$:${org.apache.river.qa.home}$/lib$/$qajinidep$:${org.apache.river.jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService 1
org.apache.river.test.impl.start.ClasspathTest1.type=activatable
org.apache.river.test.impl.start.ClasspathTest1.impl=org.apache.river.test.impl.start.TestServiceImpl
org.apache.river.test.impl.start.ClasspathTest1.classpath=<file:lib/qa1-start-testservice1.jar>
org.apache.river.test.impl.start.ClasspathTest1.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice1-dl.jar
org.apache.river.test.impl.start.ClasspathTest1.policyfile=<url:harness/policy/all.policy>
org.apache.river.test.impl.start.ClasspathTest1.log=none
org.apache.river.test.impl.start.ClasspathTest1.serverjvmargs=
org.apache.river.test.impl.start.ClasspathTest1.serviceConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.ClasspathTest1.starterConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.ClasspathTest1.host=master

// TestService 2 - same as above except different classpath and codebase
org.apache.river.test.impl.start.ClasspathTest2.type=activatable
org.apache.river.test.impl.start.ClasspathTest2.impl=${org.apache.river.test.impl.start.ClasspathTest1.impl}
org.apache.river.test.impl.start.ClasspathTest2.classpath=<file:lib/qa1-start-testservice2.jar>
org.apache.river.test.impl.start.ClasspathTest2.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice2-dl.jar
org.apache.river.test.impl.start.ClasspathTest2.policyfile=${org.apache.river.test.impl.start.ClasspathTest1.policyfile}
org.apache.river.test.impl.start.ClasspathTest2.log=${org.apache.river.test.impl.start.ClasspathTest1.log}
org.apache.river.test.impl.start.ClasspathTest2.serverjvmargs=${org.apache.river.test.impl.start.ClasspathTest1.serverjvmargs}
org.apache.river.test.impl.start.ClasspathTest2.serviceConfiguration=${org.apache.river.test.impl.start.ClasspathTest1.serviceConfiguration}
org.apache.river.test.impl.start.ClasspathTest2.starterConfiguration=${org.apache.river.test.impl.start.ClasspathTest1.starterConfiguration}
org.apache.river.test.impl.start.ClasspathTest2.host=master

// Shared group overrides
sharedGroup.classpath=${org.apache.river.jsk.home}$/lib$/sharedvm.jar$:${org.apache.river.jsk.home}$/lib$/high-scale-lib.jar$:${org.apache.river.jsk.home}$/lib$/custard-apple-1.0.3.jar$:<file:lib/qa1-start-testservice-common.jar>
sharedGroup.codebase=http://${HOST}:${org.apache.river.jsk.port}/create-dl.jar http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice-common-dl.jar
sharedGroup.policy=<url:harness/policy/all.policy>
include0=start.properties
