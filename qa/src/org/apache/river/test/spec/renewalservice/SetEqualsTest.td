testClass=SetEqualsTest
testCategories=renewalservice,renewalservice_spec
include0=renewalservice.properties

# applies to all Lookup services started for the tests using this file
net.jini.core.lookup.ServiceRegistrar.log.0=$/tmp$/reggie_log01
net.jini.core.lookup.ServiceRegistrar.log.1=$/tmp$/reggie_log02

# the number of LRS instances to start for this test
net.jini.lease.LeaseRenewalService.instances=2
