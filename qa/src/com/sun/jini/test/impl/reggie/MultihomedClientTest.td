testClass=MultihomedClientTest
testCategories=lookupservice,lookupservice_impl
testPolicyfile=<url: harness/policy/policy.all>

policy=<url: harness/policy/policy.all>
nameservice=com.sun.jini.qa.reggie,com.sun.jini.qa.reggie
lib-dl=${com.sun.jini.jsk.home}/lib-dl/
toolClassPath=${com.sun.jini.jsk.home}/lib/classserver.jar
reggieClasses=${com.sun.jini.jsk.home}/lib/reggie.jar
reggieConfig=<url:com/sun/jini/test/impl/reggie/resources/reggie.config>
startConfig=<url:com/sun/jini/test/impl/reggie/resources/start.config>
lookupConfig=<url:com/sun/jini/test/impl/reggie/resources/lookupdiscovery.config>
logging=<file:tests/logging/multihomedClientTestLogging.properties>
metaInf=<file: lib/qa1-multihomedmetainf.jar>
timeout=10

