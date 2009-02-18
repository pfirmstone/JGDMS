testClass=MailboxImplReadyStateTest
testCategories=eventmailbox,eventmailbox_impl

net.jini.event.EventMailbox.serviceConfiguration=<url:mercuryFixedExporter.config>
net.jini.event.EventMailbox.classpath=${net.jini.event.EventMailbox.classpath}$:<file:lib/qa1-mercury-bogusimpl.jar>
net.jini.event.EventMailbox.policyfile=<url:com/sun/jini/test/impl/mahalo/bogusMahaloImpl.policy>

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.test.impl.mercury.ActivatableMercuryVerifier com.sun.jini.qa.harness.SkipConfigTestVerifier
com.sun.jini.qa.harness.skipConfigs=general,http,https,jrmp,jsse,kerberos

include0=mercury.properties
