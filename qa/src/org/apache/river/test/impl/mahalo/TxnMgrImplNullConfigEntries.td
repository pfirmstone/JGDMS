include0=mahalo.properties
testClass=TxnMgrImplNullConfigEntries
testCategories=txnmanager,txnmanager_impl

net.jini.core.transaction.server.TransactionManager.instances=9

net.jini.core.transaction.server.TransactionManager.override.0=org.apache.river.mahalo.serverExporter=null
net.jini.core.transaction.server.TransactionManager.override.1=org.apache.river.mahalo.initialLookupAttributes=null
net.jini.core.transaction.server.TransactionManager.override.2=org.apache.river.mahalo.initialLookupLocators=null
net.jini.core.transaction.server.TransactionManager.override.3=org.apache.river.mahalo.leasePeriodPolicy=null
net.jini.core.transaction.server.TransactionManager.override.4=org.apache.river.mahalo.participantPreparer=null
net.jini.core.transaction.server.TransactionManager.override.5=org.apache.river.mahalo.lookupLocatorPreparer=null
net.jini.core.transaction.server.TransactionManager.override.6=org.apache.river.mahalo.discoveryManager=null
net.jini.core.transaction.server.TransactionManager.override.7=org.apache.river.mahalo.settlerPool=null
net.jini.core.transaction.server.TransactionManager.override.8=org.apache.river.mahalo.taskPool=null

# Not obtained unless there are any locators to recover
#net.jini.core.transaction.server.TransactionManager.override.10=org.apache.river.mahalo.recoveredLookupLocatorPreparer=null
