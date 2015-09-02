# Skip test verifier until test is confirmed by Jini Community that 
# Events should be prioritsed in order of registered services.
#org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier
testClass=NotifyOnComboAttrDelNonNull
testCategories=lookupservice,lookupservice_spec,test_set01
include0=../lookupservice.properties

org.apache.river.test.spec.lookupservice.nInstancesPerClass=2
