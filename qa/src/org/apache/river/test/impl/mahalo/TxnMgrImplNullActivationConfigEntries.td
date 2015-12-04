include0=mahalo.properties
testClass=TxnMgrImplNullConfigEntries
testCategories=txnmanager,txnmanager_impl

net.jini.core.transaction.server.TransactionManager.instances=4

net.jini.core.transaction.server.TransactionManager.override.0=org.apache.river.mahalo.activationIdPreparer=null
net.jini.core.transaction.server.TransactionManager.override.1=org.apache.river.mahalo.activationSystemPreparer=null
net.jini.core.transaction.server.TransactionManager.override.6=org.apache.river.mahalo.persistenceDirectory=null
net.jini.core.transaction.server.TransactionManager.override.7=org.apache.river.mahalo.recoveredParticipantPreparer=null

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.test.impl.mahalo.ActivatableMahaloVerifier

# Not obtained unless there are any locators to recover
#net.jini.core.transaction.server.TransactionManager.override.10=org.apache.river.mahalo.recoveredLookupLocatorPreparer=null
