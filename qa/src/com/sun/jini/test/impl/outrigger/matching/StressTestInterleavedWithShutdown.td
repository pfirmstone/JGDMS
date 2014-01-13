testClass=StressTestInterleavedWithShutdown
testCategories=javaspace,javaspace_impl,javaspace_impl_matching,stress
com.sun.jini.qa.harness.timeout=14400
include0=../outrigger.properties

## This test is failing on more than just Linux.  I'm temporarily
## removing it from the test run for Porter beta.
#com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipLinuxTestVerifier
#com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipTestVerifier

com.sun.jini.test.share.restart_wait=10000
com.sun.jini.test.impl.outrigger.matching.StressTest.num_entries=10000
com.sun.jini.test.impl.outrigger.matching.StressTest.num_readers=400
com.sun.jini.test.impl.outrigger.matching.StressTest.num_writers=250
com.sun.jini.test.impl.outrigger.matching.StressTest.interleave=true
com.sun.jini.test.impl.outrigger.matching.tryShutdown=1

