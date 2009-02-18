include0=mahalo.properties
testClass=TxnMgrImplNullConfigEntries
testCategories=txnmanager,txnmanager_impl

net.jini.core.transaction.server.TransactionManager.instances=4

net.jini.core.transaction.server.TransactionManager.override.0=com.sun.jini.mahalo.activationIdPreparer=null
net.jini.core.transaction.server.TransactionManager.override.1=com.sun.jini.mahalo.activationSystemPreparer=null
net.jini.core.transaction.server.TransactionManager.override.6=com.sun.jini.mahalo.persistenceDirectory=null
net.jini.core.transaction.server.TransactionManager.override.7=com.sun.jini.mahalo.recoveredParticipantPreparer=null

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.test.impl.mahalo.ActivatableMahaloVerifier

# Not obtained unless there are any locators to recover
#net.jini.core.transaction.server.TransactionManager.override.10=com.sun.jini.mahalo.recoveredLookupLocatorPreparer=null
