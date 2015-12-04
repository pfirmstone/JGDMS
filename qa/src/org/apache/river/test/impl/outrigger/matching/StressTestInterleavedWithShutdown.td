testClass=StressTestInterleavedWithShutdown
testCategories=javaspace,javaspace_impl,javaspace_impl_matching,stress
org.apache.river.qa.harness.timeout=14400
include0=../outrigger.properties

## This test is failing on more than just Linux.  I'm temporarily
## removing it from the test run for Porter beta.
#org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipLinuxTestVerifier
#org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier

org.apache.river.test.share.restart_wait=10000
org.apache.river.test.impl.outrigger.matching.StressTest.num_entries=5000
org.apache.river.test.impl.outrigger.matching.StressTest.num_readers=100
org.apache.river.test.impl.outrigger.matching.StressTest.num_writers=25
org.apache.river.test.impl.outrigger.matching.StressTest.interleave=true
org.apache.river.test.impl.outrigger.matching.tryShutdown=1

