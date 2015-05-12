testClass=StressTest
testCategories=javaspace,javaspace_impl,javaspace_impl_matching,stress
org.apache.river.qa.harness.timeout=14400
#org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipLinuxTestVerifier
include0=../outrigger.properties

org.apache.river.test.share.restart_wait=10000
org.apache.river.test.impl.outrigger.matching.StressTest.num_entries=1000
org.apache.river.test.impl.outrigger.matching.StressTest.num_readers=13
org.apache.river.test.impl.outrigger.matching.StressTest.num_writers=7
