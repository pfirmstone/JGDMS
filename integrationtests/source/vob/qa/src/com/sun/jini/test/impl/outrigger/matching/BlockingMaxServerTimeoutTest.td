testClass=BlockingTest
testCategories=javaspace,javaspace_impl,javaspace_impl_matching

com.sun.jini.qa.outrigger.matching.BlockingTest.matchTimeout=90000
com.sun.jini.qa.outrigger.matching.BlockingTest.writeWait=60000

#no cosmetic whitespace allowed for testjvmargs, parsed on ,
testjvmargs=${testjvmargs},-Dcom.sun.jini.outrigger.maxServerQueryTimeout=30000
include0=../outrigger.properties
