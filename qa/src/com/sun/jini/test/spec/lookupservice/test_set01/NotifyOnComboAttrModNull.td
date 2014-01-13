# Skip test verifier until test is confirmed by Jini Community that 
# Events should be prioritsed in order of registered services.
com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipTestVerifier
testClass=NotifyOnComboAttrModNull
testCategories=lookupservice,lookupservice_spec,test_set01
include0=../lookupservice.properties

com.sun.jini.test.spec.lookupservice.nInstancesPerClass=2
