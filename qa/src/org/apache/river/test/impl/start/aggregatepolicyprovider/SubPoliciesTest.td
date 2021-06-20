testClass=SubPoliciesTest
testCategories=start,start_impl,policyprovider
#testClasspath=${qa.home}$/lib$/harness.jar$:${qa.home}$/lib$/qa1-start-tests.jar$:${qa.home}$/lib$/$qajinidep$:${jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>
testPolicyfile=SubPoliciesTest.0.policy

#testjvmargs=\
#-agentlib:jdwp=transport=dt_shmem+,address=shmem+,server=y+,suspend=y,\
#${testjvmargs}

#!!!! the next two lines are wrong
policy0File=<url:SubPoliciesTest.0.policy>
policy1File=<url:SubPoliciesTest.1.policy>

include0=../start.properties
