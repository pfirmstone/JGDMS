testClass=Resolve_LoadClassTest
testCategories=io,io_spec
testClasspath=<harnessJar>$:<file:lib/qa1-io-marshalinputstream-test.jar>$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar$:${com.sun.jini.jsk.home}$/lib$/jsk-lib.jar
testjvmargs=-Djava.rmi.server.RMIClassLoaderSpi=com.sun.jini.test.spec.io.util.FakeRMIClassLoaderSpi
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
include0=../io.properties
