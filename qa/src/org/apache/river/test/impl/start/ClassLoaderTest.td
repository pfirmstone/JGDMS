testClass=ClassLoaderTest
testCategories=start,start_impl
#testClasspath=${org.apache.river.qa.home}$/lib$/harness.jar$:${org.apache.river.qa.home}$/lib$/qa1-start-tests.jar$:${org.apache.river.qa.home}$/lib$/$qajinidep$:${org.apache.river.jsk.home}$/lib$/jsk-platform.jar

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
sharedGroup.classpath=${org.apache.river.jsk.home}$/lib$/${org.apache.river.start.jar}$:${org.apache.river.jsk.home}$/lib$/${org.cliffc.high_scale_lib.jar}$:<file:lib/qa1-start-testservice-common.jar>$:${org.apache.river.jsk.home}$/lib$/${org.apache.river.phoenix.init.jar}$:${org.apache.river.jsk.home}$/lib$/${org.apache.river.phoenix.group.jar}$:${org.apache.river.jsk.home}$/lib-dl$/${org.apache.river.phoenix.dl.jar}$:${org.apache.river.jsk.home}$/lib$/${org.apache.river.phoenix.common.jar}$:${org.apache.river.jsk.home}$/lib$/${net.jini.platform.jar}$:${org.apache.river.jsk.home}$/lib$/${org.apache.river.collections.jar}$:${org.apache.river.jsk.home}$/lib$/${org.apache.river.lib.jar}$:${org.apache.river.jsk.home}$/lib-dl$/${net.jini.lib.dl.jar}$:${org.apache.river.jsk.home}$/lib$/${net.jini.jeri.jar}$:${org.apache.river.jsk.home}$/lib$/${net.jini.activation.jar}$:${org.apache.river.jsk.home}$/lib$/${net.jini.activation-param.jar}$:${org.apache.river.jsk.home}$/lib$/${net.jini.pref.jar}$:${org.apache.river.jsk.home}$/lib$/${jsk.url.jar}$:${org.apache.river.jsk.home}$/lib$/${jgdms-rmi-tls.jar}
