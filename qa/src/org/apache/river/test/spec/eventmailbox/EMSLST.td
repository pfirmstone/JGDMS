testClass=EMSLST
testCategories=eventmailbox,eventmailbox_spec,stress

#org.apache.river.test.impl.mercury.num_regs=10
#org.apache.river.test.impl.mercury.num_events_per_reg=1000

include0=mercury.properties
testjvmargs=${testjvmargs},\
-Djava.rmi.server.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-mercury-dl.jar