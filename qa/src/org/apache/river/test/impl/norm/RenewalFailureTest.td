# Test creates a lease a number of leases, places them all in the
# renewal set and then forces a number of failure and makes sure that
# the appropriate events get generated, and that the leases are removed
# from the set.

testClass=RenewalFailureTest
testCategories=renewalservice,renewalservice_impl

org.apache.river.test.impl.norm.renew_grant=300000
org.apache.river.test.impl.norm.renew_count=2
org.apache.river.test.impl.norm.tryShutdown=true
include0=norm.properties
testLoggingProperties=<file: src/org/apache/river/test/resources/qa1_debug.logging>
