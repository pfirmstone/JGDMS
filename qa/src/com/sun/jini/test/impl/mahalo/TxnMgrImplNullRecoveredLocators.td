include0=mahalo.properties
testClass=TxnMgrImplNullRecoveredLocators
testCategories=txnmanager,txnmanager_impl

net.jini.core.transaction.server.TransactionManager.override.0=com.sun.jini.mahalo.initialLookupLocators=new net.jini.core.discovery.LookupLocator[] { new net.jini.core.discovery.LookupLocator(\\u0022jini://localhost/\\u0022) }|com.sun.jini.mahalo.recoveredLookupLocatorPreparer=null 

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.test.impl.mahalo.ActivatableMahaloVerifier
