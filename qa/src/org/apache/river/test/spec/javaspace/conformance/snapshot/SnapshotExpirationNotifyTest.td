#Test fails randomly, temporarily suspended pending release 2.3.0
#org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier
testClass=SnapshotExpirationNotifyTest
testCategories=javaspace,javaspace_spec,javaspace_conformance,snapshot
include0=../javaspace.properties
# timeout2 must be greater than (timeout1 + instantTime)
# it is recommended that timeout2 be greater than (timeout1 + 2*instantTime)
org.apache.river.test.spec.javaspace.conformance.timeout1=12000
# SSL Endpoints need a little more time for this test to pass :)
org.apache.river.test.spec.javaspace.conformance.timeout2=38000
# this value is used for checking read and take invocations*
org.apache.river.test.spec.javaspace.conformance.checkTime=15000
#  general round trip time expected to non-blocking operations.
#  should be set to checkTime / 2. 
org.apache.river.test.spec.javaspace.conformance.instantTime = 7500
