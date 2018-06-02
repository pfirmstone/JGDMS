testClass=PreparedTransactionTestAbortActive
testCategories=javaspace,javaspace_impl,javaspace_impl_transaction

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.test.impl.outrigger.ActivatableOutriggerVerifier
include0=../outrigger.properties

org.apache.river.qa.outrigger.transaction.PreparedTransactionTest.active=true
org.apache.river.qa.harness.globalvmargs=${org.apache.river.qa.harness.globalvmargs},\
-Dnet.jini.jeri.ONLY_VALIDATE_INPUT_IF_CONSTRAINT_SET=true