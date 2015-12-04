testClass=Resolve_LoadClassTest
testCategories=io,io_spec
testClasspath=${altClasspath}$:<file:lib/qa1-io-marshalinputstream-test.jar>
testjvmargs=-Dnet.jini.loader.ClassLoading.provider=org.apache.river.test.spec.io.util.FakeRMIClassLoaderSpi
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
include0=../io.properties
