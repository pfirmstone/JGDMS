testClass=InterOpTest
testCategories=eventmailbox,eventmailbox_impl

net.jini.space.JavaSpace.tojoin=foo
net.jini.event.EventMailbox.tojoin=foo
net.jini.core.lookup.ServiceRegistrar.membergroups=foo

include0=mercury.properties
testjvmargs=${testjvmargs},\
-Djava.rmi.server.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-mercury-dl.jar
