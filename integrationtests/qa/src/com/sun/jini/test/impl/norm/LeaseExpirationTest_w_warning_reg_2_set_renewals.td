# Test creates a number of leases places them in a renewal set and lets
# the set's lease expire.  Fails if the leases it places in the set
# are renewed after the set expires.

testClass=LeaseExpirationTest
testCategories=renewalservice,renewalservice_impl

com.sun.jini.test.impl.norm.min_warning=30000
com.sun.jini.test.impl.norm.renew_grant=20000
com.sun.jini.test.impl.norm.lease_length=150000
com.sun.jini.test.impl.norm.lease_count=20
com.sun.jini.test.impl.norm.warning=true
com.sun.jini.test.impl.norm.set_renewals=2
include0=norm.properties
