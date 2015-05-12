testClass=ServiceStarterCreateActivatableNoConsServiceTest
testCategories=start,start_impl


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService with no activatable constructor
org.apache.river.test.impl.start.TestServiceNoConsImpl.impl=org.apache.river.test.impl.start.TestServiceNoConsImpl
org.apache.river.test.impl.start.TestServiceNoConsImpl.classpath=<file:lib/qa1-start-testservicenocons.jar>
org.apache.river.test.impl.start.TestServiceNoConsImpl.codebase=http://${HOST}:${org.apache.river.test.port}/qa1-start-testservicenocons-dl.jar
org.apache.river.test.impl.start.TestServiceNoConsImpl.policyfile=<url:harness/policy/all.policy>
org.apache.river.test.impl.start.TestServiceNoConsImpl.log=none
org.apache.river.test.impl.start.TestServiceNoConsImpl.serverjvmargs=
org.apache.river.test.impl.start.TestServiceNoConsImpl.serviceConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.TestServiceNoConsImpl.starterConfiguration=<url:configs/<config>/testservice.config>
org.apache.river.test.impl.start.TestServiceNoConsImpl.type=transient


// Shared group overrides
sharedGroup.policyfile=<url:harness/policy/all.policy>
#sharedGroupImpl.policyfile=<url:harness/policy/policy.SecurityTest.sharedgroup>
include0=start.properties
