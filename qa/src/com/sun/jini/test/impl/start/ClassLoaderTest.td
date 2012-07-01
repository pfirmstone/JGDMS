testClass=ClassLoaderTest
testCategories=start,start_impl
#testClasspath=${com.sun.jini.qa.home}$/lib$/harness.jar$:${com.sun.jini.qa.home}$/lib$/qa1-start-tests.jar$:${com.sun.jini.qa.home}$/lib$/$qajinidep$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService 1
com.sun.jini.test.impl.start.ClassLoaderTest1.type=activatable
com.sun.jini.test.impl.start.ClassLoaderTest1.impl=com.sun.jini.test.impl.start.TestServiceImpl
com.sun.jini.test.impl.start.ClassLoaderTest1.classpath=<file:lib/qa1-start-testservice1.jar>
com.sun.jini.test.impl.start.ClassLoaderTest1.codebase=http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice1-dl.jar
com.sun.jini.test.impl.start.ClassLoaderTest1.policyfile=<url:harness/policy/all.policy>
com.sun.jini.test.impl.start.ClassLoaderTest1.log=none
com.sun.jini.test.impl.start.ClassLoaderTest1.serviceConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.ClassLoaderTest1.starterConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.ClassLoaderTest1.host=master

// TestService 2 - same as above
com.sun.jini.test.impl.start.ClassLoaderTest2.type=activatable
com.sun.jini.test.impl.start.ClassLoaderTest2.impl=${com.sun.jini.test.impl.start.ClassLoaderTest1.impl}
com.sun.jini.test.impl.start.ClassLoaderTest2.classpath=${com.sun.jini.test.impl.start.ClassLoaderTest1.classpath}
com.sun.jini.test.impl.start.ClassLoaderTest2.codebase=${com.sun.jini.test.impl.start.ClassLoaderTest1.codebase}
com.sun.jini.test.impl.start.ClassLoaderTest2.policyfile=${com.sun.jini.test.impl.start.ClassLoaderTest1.policyfile}
com.sun.jini.test.impl.start.ClassLoaderTest2.log=${com.sun.jini.test.impl.start.ClassLoaderTest1.log}
com.sun.jini.test.impl.start.ClassLoaderTest2.serverjvmargs=${com.sun.jini.test.impl.start.ClassLoaderTest1.serverjvmargs}
com.sun.jini.test.impl.start.ClassLoaderTest2.serviceConfiguration=${com.sun.jini.test.impl.start.ClassLoaderTest1.serviceConfiguration}
com.sun.jini.test.impl.start.ClassLoaderTest2.starterConfiguration=${com.sun.jini.test.impl.start.ClassLoaderTest1.starterConfiguration}
com.sun.jini.test.impl.start.ClassLoaderTest2.host=master

include0=start.properties
sharedGroup.classpath=${com.sun.jini.jsk.home}$/lib$/sharedvm.jar$:${com.sun.jini.jsk.home}$/lib$/reference-collections-1.0.1.jar$:<file:lib/qa1-start-testservice-common.jar>
