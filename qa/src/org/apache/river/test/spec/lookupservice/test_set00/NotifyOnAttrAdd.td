# Skip test verifier until test is confirmed by Jini Community that 
# Events should be prioritsed in order of registered services.
#org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier
testClass=NotifyOnAttrAdd
testCategories=lookupservice,lookupservice_spec,test_set00
include0=../lookupservice.properties
#testjvmargs=-Djava.security.debug=access,\
testjvmargs=-Dorg.apache.river.test.spec.lookupservice.deltaTListener=30,\
#testjvmargs=-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
${testjvmargs}