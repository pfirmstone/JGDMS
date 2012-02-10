testClass=SecurityTestNonActivatable
testCategories=start,start_impl

#testClasspath=${com.sun.jini.qa.home}$/lib$/harness.jar$:${com.sun.jini.qa.home}$/lib$/qa1-start-tests.jar$:${com.sun.jini.qa.home}$/lib$/$qajinidep$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService 1
com.sun.jini.test.impl.start.SecurityTest1.impl=com.sun.jini.test.impl.start.TestServiceImpl
com.sun.jini.test.impl.start.SecurityTest1.classpath=<file:lib/qa1-start-testservice1.jar>
com.sun.jini.test.impl.start.SecurityTest1.codebase=http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice1-dl.jar
com.sun.jini.test.impl.start.SecurityTest1.policyfile=<url:securityTest.testservice1.policy>
com.sun.jini.test.impl.start.SecurityTest1.log=none
com.sun.jini.test.impl.start.SecurityTest1.serverjvmargs=
com.sun.jini.test.impl.start.SecurityTest1.serviceConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.SecurityTest1.starterConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.SecurityTest1.type=transient
com.sun.jini.test.impl.start.SecurityTest1.host=master

// TestService 2 - same as above except different classpath, codebase, and policy file
com.sun.jini.test.impl.start.SecurityTest2.impl=${com.sun.jini.test.impl.start.SecurityTest1.impl}
com.sun.jini.test.impl.start.SecurityTest2.classpath=<file:lib/qa1-start-testservice2.jar>
com.sun.jini.test.impl.start.SecurityTest2.codebase=http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservice2-dl.jar
com.sun.jini.test.impl.start.SecurityTest2.policyfile=<url:securityTest.testservice2.policy>
com.sun.jini.test.impl.start.SecurityTest2.log=${com.sun.jini.test.impl.start.SecurityTest1.log}
com.sun.jini.test.impl.start.SecurityTest2.serverjvmargs=${com.sun.jini.test.impl.start.SecurityTest1.serverjvmargs}
com.sun.jini.test.impl.start.SecurityTest2.serviceConfiguration=${com.sun.jini.test.impl.start.SecurityTest1.serviceConfiguration}
com.sun.jini.test.impl.start.SecurityTest2.starterConfiguration=${com.sun.jini.test.impl.start.SecurityTest1.starterConfiguration}
com.sun.jini.test.impl.start.SecurityTest2.type=${com.sun.jini.test.impl.start.SecurityTest1.type}
com.sun.jini.test.impl.start.SecurityTest2.host=master

include0=start.properties
