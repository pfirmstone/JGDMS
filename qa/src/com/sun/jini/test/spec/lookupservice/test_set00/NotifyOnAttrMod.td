# Skip test verifier until test is confirmed by Jini Community that 
# Events should be prioritsed in order of registered services.
com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipTestVerifier
testClass=NotifyOnAttrMod
testCategories=lookupservice,lookupservice_spec,test_set00
include0=../lookupservice.properties
testjvmargs=-Dcom.sun.jini.test.spec.lookupservice.deltaTListener=30,\
${testjvmargs}
