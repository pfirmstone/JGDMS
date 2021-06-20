testClass=ClassLoaderTest
testCategories=start,start_impl
#testClasspath=${qa.home}$/lib$/harness.jar$:${qa.home}$/lib$/qa1-start-tests.jar$:${qa.home}$/lib$/$qajinidep$:${jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService 1
org.apache.river.test.impl.start.ClassLoaderTest1.type=activatable
org.apache.river.test.impl.start.ClassLoaderTest1.impl=org.apache.river.test.impl.start.TestServiceImpl
org.apache.river.test.impl.start.ClassLoaderTest1.classpath=<file:lib/qa1-start-testservice1.jar>
org.apache.river.test.impl.start.ClassLoaderTest1.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-start-testservice1-dl.jar
org.apache.river.test.impl.start.ClassLoaderTest1.policyfile=<url:harness/policy/all.policy>
org.apache.river.test.impl.start.ClassLoaderTest1.log=none
org.apache.river.test.impl.start.ClassLoaderTest1.serviceConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.ClassLoaderTest1.starterConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.ClassLoaderTest1.host=master

// TestService 2 - same as above
org.apache.river.test.impl.start.ClassLoaderTest2.type=activatable
org.apache.river.test.impl.start.ClassLoaderTest2.impl=${org.apache.river.test.impl.start.ClassLoaderTest1.impl}
org.apache.river.test.impl.start.ClassLoaderTest2.classpath=${org.apache.river.test.impl.start.ClassLoaderTest1.classpath}
org.apache.river.test.impl.start.ClassLoaderTest2.codebase=${org.apache.river.test.impl.start.ClassLoaderTest1.codebase}
org.apache.river.test.impl.start.ClassLoaderTest2.policyfile=${org.apache.river.test.impl.start.ClassLoaderTest1.policyfile}
org.apache.river.test.impl.start.ClassLoaderTest2.log=${org.apache.river.test.impl.start.ClassLoaderTest1.log}
org.apache.river.test.impl.start.ClassLoaderTest2.serverjvmargs=${org.apache.river.test.impl.start.ClassLoaderTest1.serverjvmargs}
org.apache.river.test.impl.start.ClassLoaderTest2.serviceConfiguration=${org.apache.river.test.impl.start.ClassLoaderTest1.serviceConfiguration}
org.apache.river.test.impl.start.ClassLoaderTest2.starterConfiguration=${org.apache.river.test.impl.start.ClassLoaderTest1.starterConfiguration}
org.apache.river.test.impl.start.ClassLoaderTest2.host=master

include0=start.properties
sharedGroup.classpath=${jsk.home}$/lib$/${start.jar}$:${jsk.home}$/lib$/${high-scale-lib.jar}$:<file:lib/qa1-start-testservice-common.jar>$:${jsk.home}$/lib$/${phoenix-init.jar}$:${jsk.home}$/lib$/${phoenix-group.jar}$:${jsk.home}$/lib-dl$/${phoenix-dl.jar}$:${jsk.home}$/lib$/${phoenix-common.jar}$:${jsk.home}$/lib$/${platform.jar}$:${jsk.home}$/lib$/${collections.jar}$:${jsk.home}$/lib$/${jsk-lib.jar}$:${jsk.home}$/lib-dl$/${jsk-dl.jar}$:${jsk.home}$/lib$/${jeri.jar}$:${jsk.home}$/lib$/${activation.jar}$:${jsk.home}$/lib$/${activation-param.jar}$:${jsk.home}$/lib$/${jsk.pref.jar}$:${jsk.home}$/lib$/${jsk.url.jar}$:${jsk.home}$/lib$/${jgdms-rmi-tls.jar}
