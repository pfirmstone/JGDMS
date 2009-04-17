testClass=MailboxImplBadUnexportConfigEntries
testCategories=eventmailbox,eventmailbox_impl

net.jini.event.EventMailbox.instances=2

net.jini.event.EventMailbox.override.0=com.sun.jini.mercury.maxUnexportDelay=-1
net.jini.event.EventMailbox.override.1=com.sun.jini.mercury.unexportRetryDelay=0
include0=mercury.properties
