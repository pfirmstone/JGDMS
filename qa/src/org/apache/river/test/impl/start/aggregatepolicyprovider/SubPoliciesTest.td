testClass=SubPoliciesTest
testCategories=start,start_impl,policyprovider
#testClasspath=${org.apache.river.qa.home}$/lib$/harness.jar$:${org.apache.river.qa.home}$/lib$/qa1-start-tests.jar$:${org.apache.river.qa.home}$/lib$/$qajinidep$:${org.apache.river.jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:<file:lib/qa1-start-tests.jar>
testPolicyfile=SubPoliciesTest.0.policy

#testjvmargs=\
#-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}

#!!!! the next two lines are wrong
policy0File=<url:SubPoliciesTest.0.policy>
policy1File=<url:SubPoliciesTest.1.policy>

include0=../start.properties
