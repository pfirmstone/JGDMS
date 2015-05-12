# Test creates a lease a number of leases and adds them with
# diffrent desiered expiration and renew durations wait for
# some time and makes sure that only the leases that should
# have expired expire.

testClass=BatchRenewTest
testCategories=renewalservice,renewalservice_impl

org.apache.river.test.impl.norm.tryShutdown=true
include0=norm.properties
