include0=mahalo.properties
testClass=RandomStressTest
testCategories=txnmanager,txnmanager_impl,stress

# test may need up to 3 hours to run on an Ultra1
# value specified in seconds
org.apache.river.qa.harness.timeout=10800
org.apache.river.test.impl.mahalo.RandomStressTest.parts=10
org.apache.river.test.impl.mahalo.RandomStressTest.tasks=1000
#org.apache.river.test.impl.mahalo.RandomStressTest.seed=1620791630932
