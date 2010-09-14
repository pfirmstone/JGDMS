# Test creates a lease, places it in a lease renewal set, waits for
# to be renewed a designated number of times and then removes it.

testClass=AddRenewRemoveTest
testCategories=renewalservice,renewalservice_impl

com.sun.jini.test.impl.norm.renew_grant=60000
com.sun.jini.test.impl.norm.tryShutdown=true
include0=norm.properties
testLoggingProperties=<file: src/com/sun/jini/test/resources/qa1_debug.logging>
