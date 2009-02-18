testClass=SubPoliciesTest
testCategories=start,start_impl
#testClasspath=${com.sun.jini.qa.home}$/lib$/harness.jar$:${com.sun.jini.qa.home}$/lib$/qa1-start-tests.jar$:${com.sun.jini.qa.home}$/lib$/$qajinidep$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar

testClasspath=<harnessJar>$:<file:lib/qa1-start-tests.jar>$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar$:${com.sun.jini.jsk.home}$/lib$/jsk-lib.jar
testPolicyfile=SubPoliciesTest.0.policy

#!!!! the next two lines are wrong
policy0File=<url:SubPoliciesTest.0.policy>
policy1File=<url:SubPoliciesTest.1.policy>

include0=../start.properties
