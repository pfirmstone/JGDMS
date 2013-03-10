testClass=SnapshotExpirationNotifyTest
testCategories=javaspace,javaspace_spec,javaspace_conformance,snapshot
include0=../javaspace.properties
# timeout2 must be greater than (timeout1 + instantTime)
# it is recommended that timeout2 be greater than (timeout1 + 2*instantTime)
com.sun.jini.test.spec.javaspace.conformance.timeout1=9990
com.sun.jini.test.spec.javaspace.conformance.timeout2=19990
# this value is used for checking read and take invocations*
com.sun.jini.test.spec.javaspace.conformance.checkTime=10000
#  general round trip time expected to non-blocking operations.
#  should be set to checkTime / 2. 
com.sun.jini.test.spec.javaspace.conformance.instantTime = 5000
