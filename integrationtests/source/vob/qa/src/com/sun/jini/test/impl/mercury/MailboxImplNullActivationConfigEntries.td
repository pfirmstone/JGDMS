testClass=MailboxImplNullConfigEntries
testCategories=eventmailbox,eventmailbox_impl

net.jini.event.EventMailbox.instances=5

net.jini.event.EventMailbox.override.0=com.sun.jini.mercury.activationIdPreparer=null
net.jini.event.EventMailbox.override.1=com.sun.jini.mercury.activationSystemPreparer=null
net.jini.event.EventMailbox.override.2=com.sun.jini.mercury.persistenceDirectory=null
net.jini.event.EventMailbox.override.3=com.sun.jini.mercury.recoveredLocatorToJoinPreparer=null
net.jini.event.EventMailbox.override.4=com.sun.jini.mercury.recoveredListenerPreparer=null

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.test.impl.mercury.ActivatableMercuryVerifier
include0=mercury.properties
