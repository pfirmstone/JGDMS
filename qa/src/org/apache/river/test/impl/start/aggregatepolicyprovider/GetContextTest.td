testClass=GetContextTest
testCategories=start,start_impl,policyprovider
#testClasspath=<harnessJar>$:${org.apache.river.qa.home}$/lib$/qa1-start-tests.jar$:${org.apache.river.qa.home}$/lib$/$qajinidep$:${org.apache.river.jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:${org.apache.river.qa.home}/lib/qa1-start-tests.jar
testPolicyfile=GetContextTest.policy
getContextJarFile=${org.apache.river.qa.home}/lib/qa1-start-cb1.jar
restoreContextJarFile=${org.apache.river.qa.home}/lib/qa1-start-cb2.jar
checkContextActionJarFile=${org.apache.river.qa.home}/lib/qa1-start-cb3.jar
include0=../start.properties
#testjvmargs=\
#-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}