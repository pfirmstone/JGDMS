testClass=LoadClass_ExceptionTest
testCategories=io,io_spec
testClasspath=${altClasspath}$:<file:lib/qa1-io-marshalinputstream-test.jar>
testjvmargs=-Dnet.jini.loader.ClassLoading.provider=com.sun.jini.test.spec.io.util.FakeRMIClassLoaderSpi

com.sun.jini.test.spec.io.util.fakeArgumentJar=http://${HOST}:${com.sun.jini.test.port}/qa1-io-util-dl.jar
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
include0=../io.properties
