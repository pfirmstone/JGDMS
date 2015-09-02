testClass=DiscoveredStagger
testCategories=locatordiscovery,locatordiscovery_spec,speclocatordiscovery
include0=locatordiscovery.properties
include1=org/apache/river/test/share/reggie6_0Ports.properties

# Special properties file intended to be used by the
# org.apache.river.test.spec.locatordiscovery.DiscoveredStagger test

# Number of seconds to wait for discovered/discarded/changed events is set high
# to allow slower OS's in noisy environments a chance to process events
# Since the events arrive over a long (staggered) period of time, the
# default amount of time to wait for events in this test must be overridden
# to guarantee that the appropriate wait period is applied.
net.jini.discovery.maxSecsEventWait=600

