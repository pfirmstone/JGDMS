testClass=GetContextTest
testCategories=start,start_impl,policyprovider
#testClasspath=<harnessJar>$:${qa.home}$/lib$/qa1-start-tests.jar$:${qa.home}$/lib$/$qajinidep$:${jsk.home}$/lib$/jsk-platform.jar

testClasspath=${altClasspath}$:${qa.home}/lib/qa1-start-tests.jar
testPolicyfile=GetContextTest.policy
getContextJarFile=${qa.home}/lib/qa1-start-cb1.jar
restoreContextJarFile=${qa.home}/lib/qa1-start-cb2.jar
checkContextActionJarFile=${qa.home}/lib/qa1-start-cb3.jar
include0=../start.properties
#testjvmargs=\
#-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}