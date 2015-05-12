testClass=BlockingTestRead
testCategories=javaspace,javaspace_impl,javaspace_impl_matching

org.apache.river.qa.outrigger.matching.BlockingTest.matchTimeout=90000
org.apache.river.qa.outrigger.matching.BlockingTest.writeWait=60000

#no cosmetic whitespace allowed for testjvmargs, parsed on ,
testjvmargs=${testjvmargs},-Dorg.apache.river.outrigger.maxServerQueryTimeout=30000
include0=../outrigger.properties
