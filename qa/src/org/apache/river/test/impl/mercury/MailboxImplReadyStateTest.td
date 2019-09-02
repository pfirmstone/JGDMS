testClass=MailboxImplReadyStateTest
testCategories=eventmailbox,eventmailbox_impl

net.jini.event.EventMailbox.serviceConfiguration=<url:mercuryFixedExporter.config>
net.jini.event.EventMailbox.classpath=${net.jini.event.EventMailbox.classpath}$:<file:lib/qa1-mercury-bogusimpl.jar>
net.jini.event.EventMailbox.policyfile=<url:org/apache/river/test/impl/mahalo/bogusMahaloImpl.policy>

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.test.impl.mercury.ActivatableMercuryVerifier org.apache.river.qa.harness.SkipConfigTestVerifier
org.apache.river.qa.harness.skipConfigs=general,http,https,jeri,jsse,kerberos

include0=mercury.properties
