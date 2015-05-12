# Test creates a number leases, places it in a lease renewal set, uses
# getLeases to esure that there are all still there, removes one, makes
# sure that other ones are still in the set and the removed on is not.
# Finally it lets the membership expiration elapse on one of the leases
# still in the set, call getLeses again to ensure that all the other
# leases are still present.

testClass=GetLeasesTest
testCategories=renewalservice,renewalservice_impl

org.apache.river.test.impl.norm.tryShutdown=true
org.apache.river.test.impl.norm.membership_duration=210000
include0=norm.properties
