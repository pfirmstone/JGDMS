testClass=CodebaseTest
testCategories=start,start_impl
#testClasspath=${org.apache.river.qa.home}$/lib$/harness.jar$:${org.apache.river.qa.home}$/lib$/qa1-start-tests.jar$:${org.apache.river.qa.home}$/lib$/$qajinidep$:${org.apache.river.jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService 1
org.apache.river.test.impl.start.CodebaseTest1.type=activatable
org.apache.river.test.impl.start.CodebaseTest1.impl=org.apache.river.test.impl.start.TestServiceImpl
org.apache.river.test.impl.start.CodebaseTest1.classpath=<file:lib/qa1-start-testservice1.jar>
org.apache.river.test.impl.start.CodebaseTest1.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice1-dl.jar http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice-common-dl.jar
org.apache.river.test.impl.start.CodebaseTest1.policyfile=<url:harness/policy/all.policy>
org.apache.river.test.impl.start.CodebaseTest1.log=none
org.apache.river.test.impl.start.CodebaseTest1.serverjvmargs=
org.apache.river.test.impl.start.CodebaseTest1.serviceConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.CodebaseTest1.starterConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.CodebaseTest1.host=master


// TestService 2 - same as above except different codebase
org.apache.river.test.impl.start.CodebaseTest2.type=activatable
org.apache.river.test.impl.start.CodebaseTest2.impl=${org.apache.river.test.impl.start.CodebaseTest1.impl}
org.apache.river.test.impl.start.CodebaseTest2.classpath=${org.apache.river.test.impl.start.CodebaseTest1.classpath}
org.apache.river.test.impl.start.CodebaseTest2.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice-common-dl.jar http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice2-dl.jar
org.apache.river.test.impl.start.CodebaseTest2.policyfile=${org.apache.river.test.impl.start.CodebaseTest1.policyfile}
org.apache.river.test.impl.start.CodebaseTest2.log=${org.apache.river.test.impl.start.CodebaseTest1.log}
org.apache.river.test.impl.start.CodebaseTest2.serverjvmargs=${org.apache.river.test.impl.start.CodebaseTest1.serverjvmargs}
org.apache.river.test.impl.start.CodebaseTest2.serviceConfiguration=${org.apache.river.test.impl.start.CodebaseTest1.serviceConfiguration}
org.apache.river.test.impl.start.CodebaseTest2.starterConfiguration=${org.apache.river.test.impl.start.CodebaseTest1.starterConfiguration}
org.apache.river.test.impl.start.CodebaseTest2.host=master
include0=start.properties
