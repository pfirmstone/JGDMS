testClass=LoadClass_VerifyCodebaseIntegrityTest
testCategories=io,io_spec
testClasspath=<harnessJar>$:<file:lib/qa1-io-marshalinputstream-test.jar>$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar$:${com.sun.jini.jsk.home}$/lib$/jsk-lib.jar

com.sun.jini.test.spec.io.util.fakeArgumentJar=http://${HOST}:${com.sun.jini.test.port}/qa1-io-util-dl.jar
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
include0=../io.properties
