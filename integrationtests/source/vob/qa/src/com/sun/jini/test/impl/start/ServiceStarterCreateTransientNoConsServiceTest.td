testClass=ServiceStarterCreateActivatableNoConsServiceTest
testCategories=start,start_impl


/*******************************************************************************
 * Test-specific property files
 ******************************************************************************/
// TestService with no activatable constructor
com.sun.jini.test.impl.start.TestServiceNoConsImpl.impl=com.sun.jini.test.impl.start.TestServiceNoConsImpl
com.sun.jini.test.impl.start.TestServiceNoConsImpl.classpath=<file:lib/qa1-start-testservicenocons.jar>
com.sun.jini.test.impl.start.TestServiceNoConsImpl.codebase=http://${HOST}:${com.sun.jini.test.port}/qa1-start-testservicenocons-dl.jar
com.sun.jini.test.impl.start.TestServiceNoConsImpl.policyfile=<url:harness/policy/all.policy>
com.sun.jini.test.impl.start.TestServiceNoConsImpl.log=none
com.sun.jini.test.impl.start.TestServiceNoConsImpl.serverjvmargs=
com.sun.jini.test.impl.start.TestServiceNoConsImpl.serviceConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.TestServiceNoConsImpl.starterConfiguration=<url:configs/<config>/testservice.config>
com.sun.jini.test.impl.start.TestServiceNoConsImpl.type=transient


// Shared group overrides
sharedGroup.policyfile=<url:harness/policy/all.policy>
#sharedGroupImpl.policyfile=<url:harness/policy/policy.SecurityTest.sharedgroup>
include0=start.properties
