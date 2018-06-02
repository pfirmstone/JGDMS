testClass=LeaseExpiration
testCategories=discoveryservice,discoveryservice_spec,discoveryservicelease
include0=../discoveryservice.properties

net.jini.core.lookup.ServiceRegistrar.membergroups.0=LDSGroup0,LDSGroup1
net.jini.core.lookup.ServiceRegistrar.membergroups.1=LDSGroup0,LDSGroup1
net.jini.core.lookup.ServiceRegistrar.membergroups.2=LDSGroup0,LDSGroup1

#testjvmargs=\
#-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}