testClass=ObjectMethodsTest
testCategories=io,io_spec
testClasspath=${altClasspath}$:<file:lib/qa1-io-marshalledinstance-test.jar>
testjvmargs=-Dnet.jini.loader.ClassLoading.provider=com.sun.jini.test.spec.io.util.FakeRMIClassLoaderSpi
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
include0=../io.properties
