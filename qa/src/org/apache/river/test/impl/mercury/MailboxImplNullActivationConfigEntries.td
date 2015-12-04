testClass=MailboxImplNullConfigEntries
testCategories=eventmailbox,eventmailbox_impl

net.jini.event.EventMailbox.instances=5

net.jini.event.EventMailbox.override.0=org.apache.river.mercury.activationIdPreparer=null
net.jini.event.EventMailbox.override.1=org.apache.river.mercury.activationSystemPreparer=null
net.jini.event.EventMailbox.override.2=org.apache.river.mercury.persistenceDirectory=null
net.jini.event.EventMailbox.override.3=org.apache.river.mercury.recoveredLocatorToJoinPreparer=null
net.jini.event.EventMailbox.override.4=org.apache.river.mercury.recoveredListenerPreparer=null

org.apache.river.qa.harness.verifier=${org.apache.river.qa.harness.verifier} org.apache.river.test.impl.mercury.ActivatableMercuryVerifier
include0=mercury.properties
