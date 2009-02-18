testClass=MahaloImplReadyStateTest
testCategories=txnmanager,txnmanager_impl

net.jini.core.transaction.server.TransactionManager.serviceConfiguration=<url:mahaloFixedExporter.config>
net.jini.core.transaction.server.TransactionManager.classpath=${net.jini.core.transaction.server.TransactionManager.classpath}$:<file:lib/qa1-mercury-bogusimpl.jar>
net.jini.core.transaction.server.TransactionManager.policyfile=<url:bogusMahaloImpl.policy>

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.test.impl.mercury.ActivatableMercuryVerifier com.sun.jini.qa.harness.SkipConfigTestVerifier
com.sun.jini.qa.harness.skipConfigs=general,http,https,jrmp,jsse,kerberos

include0=mahalo.properties
