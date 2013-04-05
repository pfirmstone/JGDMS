testClass=SubPoliciesTest
testCategories=start,start_impl,policyprovider
#testClasspath=${com.sun.jini.qa.home}$/lib$/harness.jar$:${com.sun.jini.qa.home}$/lib$/qa1-start-tests.jar$:${com.sun.jini.qa.home}$/lib$/$qajinidep$:${com.sun.jini.jsk.home}$/lib$/jsk-platform.jar

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
