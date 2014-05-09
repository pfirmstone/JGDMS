include0=mahalo.properties
testClass=RandomStressTest
testCategories=txnmanager,txnmanager_impl,stress

# test may need up to 3 hours to run on an Ultra1
# value specified in seconds
com.sun.jini.qa.harness.timeout=10800
com.sun.jini.test.impl.mahalo.RandomStressTest.parts=10
com.sun.jini.test.impl.mahalo.RandomStressTest.tasks=1000
