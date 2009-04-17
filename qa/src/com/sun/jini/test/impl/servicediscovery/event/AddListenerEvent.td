include0=../servicediscovery.properties
testClass=AddListenerEvent
testCategories=servicediscovery,servicediscovery_impl,servicediscoveryevent,implservicediscovery,implservicediscoveryevent
net.jini.lookup.nLookupServices=1
net.jini.lookup.nServices=1
# Not need to run activatable and/or persistent version of
# the lookp service. Uncomment the following items when
# the harness supports starting the non-activatable
# service modes.
#com.sun.jini.qa.harness.runactivation=false
#net.jini.core.lookup.ServiceRegistrar.type=transient
#net.jini.core.lookup.ServiceRegistrar.impl=com.sun.jini.reggie.TransientRegistrarImpl
