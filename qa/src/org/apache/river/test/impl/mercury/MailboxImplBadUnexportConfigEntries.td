testClass=MailboxImplBadUnexportConfigEntries
testCategories=eventmailbox,eventmailbox_impl

net.jini.event.EventMailbox.instances=2

net.jini.event.EventMailbox.override.0=org.apache.river.mercury.maxUnexportDelay=-1
net.jini.event.EventMailbox.override.1=org.apache.river.mercury.unexportRetryDelay=0
include0=mercury.properties
