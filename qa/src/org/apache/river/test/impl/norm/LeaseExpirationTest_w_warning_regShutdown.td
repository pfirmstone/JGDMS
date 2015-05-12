# Test creates a number of leases places them in a renewal set and lets
# the set's lease expire.  Fails if the leases it places in the set
# are renewed after the set expires.

testClass=LeaseExpirationTest
testCategories=renewalservice,renewalservice_impl

org.apache.river.test.impl.norm.min_warning=30000
org.apache.river.test.impl.norm.renew_grant=130000
org.apache.river.test.impl.norm.lease_length=120000
org.apache.river.test.impl.norm.lease_count=20
org.apache.river.test.impl.norm.warning=true
org.apache.river.test.impl.norm.tryShutdown=true
include0=norm.properties
