testClass=RegisterStorm
testCategories=joinmanager,joinmanager_impl,stress
include0=joinmanager.properties

# Uses the reggie implementation of the lookup service

net.jini.core.lookup.ServiceRegistrar.classpath=${jsk.home}$/lib$/reggie-service-${jgdms.version}.jar$:${jsk.home}$/lib-dl$/reggie-dl-${jgdms.version}.jar
net.jini.core.lookup.ServiceRegistrar.codebase=http://${HOST}:${org.apache.river.jsk.port}/reggie-dl-${jgdms.version}.jar http://${HOST}:${org.apache.river.jsk.port}/jgdms-lib-dl-${jgdms.version}.jar

net.jini.lookup.nLookupServices=1
net.jini.lookup.nAddLookupServices=0

net.jini.lookup.nServices=300
net.jini.lookup.nAddServices=0

net.jini.lookup.nAttributes=0
net.jini.lookup.nAddAttributes=0

net.jini.core.lookup.ServiceRegistrar.port.0=9900

testConfiguration=<url:configs/<config>/RegisterStorm.config>
