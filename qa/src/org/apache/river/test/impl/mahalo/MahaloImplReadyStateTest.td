testClass=MahaloImplReadyStateTest
testCategories=txnmanager,txnmanager_impl

net.jini.core.transaction.server.TransactionManager.serviceConfiguration=<url:mahaloFixedExporter.config>
net.jini.core.transaction.server.TransactionManager.classpath=${net.jini.core.transaction.server.TransactionManager.classpath}$:<file:lib/qa1-mercury-bogusimpl.jar>
net.jini.core.transaction.server.TransactionManager.policyfile=<url:bogusMahaloImpl.policy>

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.test.impl.mercury.ActivatableMercuryVerifier org.apache.river.qa.harness.SkipConfigTestVerifier
org.apache.river.qa.harness.skipConfigs=general,http,https,jrmp,jsse,kerberos

include0=mahalo.properties
