testClass=BlockingOnDeadTransactionTestPoll
testCategories=javaspace,javaspace_impl,javaspace_impl_transaction
include0=../outrigger.properties

org.apache.river.qa.outrigger.transaction.BlockingOnDeadTransactionTest.poll=true
org.apache.river.qa.harness.globalvmargs=${org.apache.river.qa.harness.globalvmargs},\
-Dnet.jini.jeri.ONLY_VALIDATE_INPUT_IF_CONSTRAINT_SET=true
