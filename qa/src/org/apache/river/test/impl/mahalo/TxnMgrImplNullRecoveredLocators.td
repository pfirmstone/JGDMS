include0=mahalo.properties
testClass=TxnMgrImplNullRecoveredLocators
testCategories=txnmanager,txnmanager_impl

net.jini.core.transaction.server.TransactionManager.override.0=org.apache.river.mahalo.initialLookupLocators=new net.jini.core.discovery.LookupLocator[] { new net.jini.core.discovery.LookupLocator(\\u0022jini://localhost/\\u0022) }|org.apache.river.mahalo.recoveredLookupLocatorPreparer=null 

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.test.impl.mahalo.ActivatableMahaloVerifier
