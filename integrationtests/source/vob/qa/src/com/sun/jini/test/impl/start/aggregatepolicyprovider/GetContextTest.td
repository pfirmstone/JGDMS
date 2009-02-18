testClass=GetContextTest
testCategories=start,start_impl
#testClasspath=<harnessJar>$:${com.sun.jini.qa.home}$/lib$/qa1-start-tests.jar$:${com.sun.jini.qa.home}$/lib$/$qajinidep$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar

testClasspath=<harnessJar>$:<file:lib/qa1-start-tests.jar>$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar$:${com.sun.jini.jsk.home}$/lib$/jsk-lib.jar
testPolicyfile=GetContextTest.policy
getContextJarFile=<file:lib/qa1-start-cb1.jar>
restoreContextJarFile=<file:lib/qa1-start-cb2.jar>
checkContextActionJarFile=<file:lib/qa1-start-cb3.jar>
include0=../start.properties
