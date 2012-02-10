testClass=NotifyOnAttrAdd
testCategories=lookupservice,lookupservice_spec,test_set00
include0=../lookupservice.properties
#testjvmargs=-Djava.security.debug=access,\
testjvmargs=-Dcom.sun.jini.test.spec.lookupservice.deltaTListener=20,\
#testjvmargs=-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
${testjvmargs}