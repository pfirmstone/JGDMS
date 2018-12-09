testClass=EMSCET
testCategories=eventmailbox,eventmailbox_spec
include0=mercury.properties
include1=mercury_pull.properties
testjvmargs=${testjvmargs},\
-Djava.rmi.server.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-mercury-dl.jar