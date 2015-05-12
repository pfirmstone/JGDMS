# Test creates a lease, places it in a lease renewal set, waits for
# to be renewed a designated number of times and then removes it.

testClass=AddRenewRemoveTest
testCategories=renewalservice,renewalservice_impl

org.apache.river.test.impl.norm.renew_grant=60000
org.apache.river.test.impl.norm.tryShutdown=true
include0=norm.properties
testLoggingProperties=<file: src/org/apache/river/test/resources/qa1_debug.logging>
