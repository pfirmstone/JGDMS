testClass=BlockingTestRead
testCategories=javaspace,javaspace_impl,javaspace_impl_matching
testConfiguration=<url:configs/<config>/BlockingTestReadShutdown.config>
include0=../outrigger.properties

org.apache.river.test.impl.outrigger.matching.BlockingTest.useRead=true
# The following property is now defined differently for each config
# org.apache.river.test.impl.outrigger.matching.BlockingTest.matchTimeout=50000
org.apache.river.test.impl.outrigger.matching.tryShutdown=1
