testClass=EqualsTest
testCategories=renewalservice,renewalservice_spec
include0=renewalservice.properties

# the number of LRS instances to start for this test
net.jini.lease.LeaseRenewalService.instances=2
 
# the number of times to attempt discovery before giving up
net.jini.lease.LeaseRenewal.discoveryTries=60
