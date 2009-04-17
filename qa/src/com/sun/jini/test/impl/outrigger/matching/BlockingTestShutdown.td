testClass=BlockingTestShutdown
testCategories=javaspace,javaspace_impl,javaspace_impl_matching
testConfiguration=<url:configs/<config>/BlockingTestShutdown.config>
include0=../outrigger.properties

# The following property is now defined differently for each config
# com.sun.jini.test.impl.outrigger.matching.BlockingTest.matchTimeout=50000
com.sun.jini.test.impl.outrigger.matching.tryShutdown=1
