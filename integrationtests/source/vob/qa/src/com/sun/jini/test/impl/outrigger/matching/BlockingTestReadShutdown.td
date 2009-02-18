testClass=BlockingTestRead
testCategories=javaspace,javaspace_impl,javaspace_impl_matching
testConfiguration=<url:configs/<config>/BlockingTestReadShutdown.config>
include0=../outrigger.properties

com.sun.jini.test.impl.outrigger.matching.BlockingTest.useRead=true
# The following property is now defined differently for each config
# com.sun.jini.test.impl.outrigger.matching.BlockingTest.matchTimeout=50000
com.sun.jini.test.impl.outrigger.matching.tryShutdown=1
