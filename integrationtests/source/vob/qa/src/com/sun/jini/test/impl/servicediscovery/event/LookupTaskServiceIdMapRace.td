include0=../servicediscovery.properties
testClass=LookupTaskServiceIdMapRace
testCategories=servicediscovery,servicediscovery_impl,servicediscoveryevent,implservicediscovery,implservicediscoveryevent

# Start 1 tranisent lookup service

net.jini.lookup.nLookupServices=3
net.jini.lookup.nAddLookupServices=0

com.sun.jini.qa.harness.serviceMode=transient

# No test services are pre-configured to be started by
# the harness during setup. The test itself starts the
# necessary test services during runtime.

net.jini.lookup.nServices=0
net.jini.lookup.nAddServices=0

