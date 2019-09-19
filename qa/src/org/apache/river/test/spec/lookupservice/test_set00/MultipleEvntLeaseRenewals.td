testClass=MultipleEvntLeaseRenewals
testCategories=lookupservice,lookupservice_spec,test_set00
include0=../lookupservice.properties
# 5 seconds to allow for SSL connection handshakes.
testjvmargs=-Dorg.apache.river.test.spec.lookupservice.deltaTListener=5,\
${testjvmargs}
