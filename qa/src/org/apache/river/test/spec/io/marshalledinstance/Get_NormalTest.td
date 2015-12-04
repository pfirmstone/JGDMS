testClass=Get_NormalTest
testCategories=io,io_spec
testClasspath=${altClasspath}$:<file:lib/qa1-io-marshalledinstance-test.jar>

org.apache.river.test.spec.io.util.fakeArgumentJar=http://${HOST}:${org.apache.river.test.port}/qa1-io-util-dl.jar
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
include0=../io.properties
