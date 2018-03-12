testClass=LookupAttributeAdminTestTxnMgr
testCategories=javaspace,javaspace_impl,javaspace_impl_admin
include0=../outrigger.properties


org.apache.river.test.share.administrable=net.jini.core.transaction.server.TransactionManager
org.apache.river.test.share.tryShutdown=true
org.apache.river.test.share.lookup=true

org.apache.river.qa.harness.globalvmargs=${org.apache.river.qa.harness.globalvmargs},\
-Dnet.jini.jeri.ONLY_VALIDATE_INPUT_IF_CONSTRAINT_SET=true