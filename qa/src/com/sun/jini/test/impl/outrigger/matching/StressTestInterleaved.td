testClass=StressTestInterleaved
testCategories=javaspace,javaspace_impl,javaspace_impl_matching,stress
com.sun.jini.qa.harness.timeout=14400
#com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipLinuxTestVerifier
include0=../outrigger.properties

com.sun.jini.test.share.restart_wait=10000
com.sun.jini.test.impl.outrigger.matching.StressTest.num_entries=5000
com.sun.jini.test.impl.outrigger.matching.StressTest.num_readers=50
com.sun.jini.test.impl.outrigger.matching.StressTest.num_writers=50
com.sun.jini.test.impl.outrigger.matching.StressTest.interleave=true

