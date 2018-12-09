testClass=EMSCET
testCategories=eventmailbox,eventmailbox_spec
include0=mercury.properties
testjvmargs=${testjvmargs},\
-Djava.rmi.server.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-mercury-dl.jar