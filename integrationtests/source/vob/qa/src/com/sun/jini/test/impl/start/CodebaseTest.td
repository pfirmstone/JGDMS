testClass=CodebaseTest
testCategories=start,start_impl
#testClasspath=${com.sun.jini.qa.home}$/lib$/harness.jar$:${com.sun.jini.qa.home}$/lib$/qa1-start-tests.jar$:${com.sun.jini.qa.home}$/lib$/$qajinidep$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar

testClasspath=<harnessJar>$:<file:lib/qa1-start-tests.jar>$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar$:${com.sun.jini.jsk.home}$/lib$/jsk-lib.jar


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService 1
com.sun.jini.test.impl.start.CodebaseTest1.type=activatable
com.sun.jini.test.impl.start.CodebaseTest1.impl=com.sun.jini.test.impl.start.TestServiceImpl
com.sun.jini.test.impl.start.CodebaseTest1.classpath=<file:lib/qa1-start-testservice1.jar>
com.sun.jini.test.impl.start.CodebaseTest1.codebase=http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice1-dl.jar http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice-common-dl.jar
com.sun.jini.test.impl.start.CodebaseTest1.policyfile=<url:harness/policy/all.policy>
com.sun.jini.test.impl.start.CodebaseTest1.log=none
com.sun.jini.test.impl.start.CodebaseTest1.serverjvmargs=
com.sun.jini.test.impl.start.CodebaseTest1.serviceConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.CodebaseTest1.starterConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.CodebaseTest1.host=master


// TestService 2 - same as above except different codebase
com.sun.jini.test.impl.start.CodebaseTest2.type=activatable
com.sun.jini.test.impl.start.CodebaseTest2.impl=${com.sun.jini.test.impl.start.CodebaseTest1.impl}
com.sun.jini.test.impl.start.CodebaseTest2.classpath=${com.sun.jini.test.impl.start.CodebaseTest1.classpath}
com.sun.jini.test.impl.start.CodebaseTest2.codebase=http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice-common-dl.jar http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice2-dl.jar
com.sun.jini.test.impl.start.CodebaseTest2.policyfile=${com.sun.jini.test.impl.start.CodebaseTest1.policyfile}
com.sun.jini.test.impl.start.CodebaseTest2.log=${com.sun.jini.test.impl.start.CodebaseTest1.log}
com.sun.jini.test.impl.start.CodebaseTest2.serverjvmargs=${com.sun.jini.test.impl.start.CodebaseTest1.serverjvmargs}
com.sun.jini.test.impl.start.CodebaseTest2.serviceConfiguration=${com.sun.jini.test.impl.start.CodebaseTest1.serviceConfiguration}
com.sun.jini.test.impl.start.CodebaseTest2.starterConfiguration=${com.sun.jini.test.impl.start.CodebaseTest1.starterConfiguration}
com.sun.jini.test.impl.start.CodebaseTest2.host=master
include0=start.properties
