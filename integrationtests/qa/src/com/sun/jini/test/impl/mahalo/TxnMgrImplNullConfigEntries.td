include0=mahalo.properties
testClass=TxnMgrImplNullConfigEntries
testCategories=txnmanager,txnmanager_impl

net.jini.core.transaction.server.TransactionManager.instances=9

net.jini.core.transaction.server.TransactionManager.override.0=com.sun.jini.mahalo.serverExporter=null
net.jini.core.transaction.server.TransactionManager.override.1=com.sun.jini.mahalo.initialLookupAttributes=null
net.jini.core.transaction.server.TransactionManager.override.2=com.sun.jini.mahalo.initialLookupLocators=null
net.jini.core.transaction.server.TransactionManager.override.3=com.sun.jini.mahalo.leasePeriodPolicy=null
net.jini.core.transaction.server.TransactionManager.override.4=com.sun.jini.mahalo.participantPreparer=null
net.jini.core.transaction.server.TransactionManager.override.5=com.sun.jini.mahalo.lookupLocatorPreparer=null
net.jini.core.transaction.server.TransactionManager.override.6=com.sun.jini.mahalo.discoveryManager=null
net.jini.core.transaction.server.TransactionManager.override.7=com.sun.jini.mahalo.settlerPool=null
net.jini.core.transaction.server.TransactionManager.override.8=com.sun.jini.mahalo.taskPool=null

# Not obtained unless there are any locators to recover
#net.jini.core.transaction.server.TransactionManager.override.10=com.sun.jini.mahalo.recoveredLookupLocatorPreparer=null
