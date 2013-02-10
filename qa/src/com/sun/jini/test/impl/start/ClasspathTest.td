testClass=ClasspathTest
testCategories=start,start_impl
#testClasspath=${com.sun.jini.qa.home}$/lib$/harness.jar$:${com.sun.jini.qa.home}$/lib$/qa1-start-tests.jar$:${com.sun.jini.qa.home}$/lib$/$qajinidep$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService 1
com.sun.jini.test.impl.start.ClasspathTest1.type=activatable
com.sun.jini.test.impl.start.ClasspathTest1.impl=com.sun.jini.test.impl.start.TestServiceImpl
com.sun.jini.test.impl.start.ClasspathTest1.classpath=<file:lib/qa1-start-testservice1.jar>
com.sun.jini.test.impl.start.ClasspathTest1.codebase=http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice1-dl.jar
com.sun.jini.test.impl.start.ClasspathTest1.policyfile=<url:harness/policy/all.policy>
com.sun.jini.test.impl.start.ClasspathTest1.log=none
com.sun.jini.test.impl.start.ClasspathTest1.serverjvmargs=
com.sun.jini.test.impl.start.ClasspathTest1.serviceConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.ClasspathTest1.starterConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.ClasspathTest1.host=master

// TestService 2 - same as above except different classpath and codebase
com.sun.jini.test.impl.start.ClasspathTest2.type=activatable
com.sun.jini.test.impl.start.ClasspathTest2.impl=${com.sun.jini.test.impl.start.ClasspathTest1.impl}
com.sun.jini.test.impl.start.ClasspathTest2.classpath=<file:lib/qa1-start-testservice2.jar>
com.sun.jini.test.impl.start.ClasspathTest2.codebase=http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice2-dl.jar
com.sun.jini.test.impl.start.ClasspathTest2.policyfile=${com.sun.jini.test.impl.start.ClasspathTest1.policyfile}
com.sun.jini.test.impl.start.ClasspathTest2.log=${com.sun.jini.test.impl.start.ClasspathTest1.log}
com.sun.jini.test.impl.start.ClasspathTest2.serverjvmargs=${com.sun.jini.test.impl.start.ClasspathTest1.serverjvmargs}
com.sun.jini.test.impl.start.ClasspathTest2.serviceConfiguration=${com.sun.jini.test.impl.start.ClasspathTest1.serviceConfiguration}
com.sun.jini.test.impl.start.ClasspathTest2.starterConfiguration=${com.sun.jini.test.impl.start.ClasspathTest1.starterConfiguration}
com.sun.jini.test.impl.start.ClasspathTest2.host=master

// Shared group overrides
sharedGroup.classpath=${com.sun.jini.jsk.home}$/lib$/sharedvm.jar$:${com.sun.jini.jsk.home}$/lib$/high-scale-lib.jar$:${com.sun.jini.jsk.home}$/lib$/custard-apple-1.0.2.jar$:<file:lib/qa1-start-testservice-common.jar>
sharedGroup.codebase=http://${HOST}:${com.sun.jini.jsk.port}/create-dl.jar http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice-common-dl.jar
sharedGroup.policy=<url:harness/policy/all.policy>
include0=start.properties
