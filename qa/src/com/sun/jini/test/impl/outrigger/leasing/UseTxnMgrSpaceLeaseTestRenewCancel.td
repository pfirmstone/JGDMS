#Test fails randomly, temporarily suspended pending release 2.3.0
com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipTestVerifier
testClass=UseTxnMgrSpaceLeaseTest
testCategories=javaspace,javaspace_impl,javaspace_impl_leasing
include0=../outrigger.properties

com.sun.jini.test.share.exact=true
com.sun.jini.test.share.cancel=true
com.sun.jini.test.share.renew=2
com.sun.jini.test.share.cancel_slop=1000
com.sun.jini.test.share.slop=3000
