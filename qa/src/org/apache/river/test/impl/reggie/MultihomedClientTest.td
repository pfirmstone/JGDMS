# Name service provider has been moved to an external
# library to allow compilation on platforms other than Sun JVM.
org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipNonSunJDKTestVerifier
testClass=MultihomedClientTest
testCategories=lookupservice,lookupservice_impl
testPolicyfile=<url: harness/policy/policy.all>

policy=<url: harness/policy/policy.all>
nameservice=org.apache.river.qa.reggie,org.apache.river.qa.reggie
lib-dl=${org.apache.river.jsk.home}/lib-dl/
toolClassPath=${org.apache.river.jsk.home}/lib/classserver.jar
reggieClasses=${org.apache.river.jsk.home}/lib/reggie.jar
reggieConfig=<url:org/apache/river/test/impl/reggie/resources/reggie.config>
startConfig=<url:org/apache/river/test/impl/reggie/resources/start.config>
lookupConfig=<url:org/apache/river/test/impl/reggie/resources/lookupdiscovery.config>
#logging=<file:tests/logging/multihomedClientTestLogging.properties>
logging=${java.util.logging.config.file}
metaInf=<file:lib/qa1-multihomedmetainf.jar>
metaInf2=<file:../dep-libs/reggie-test-nameservice-provider/ReggieTestNameServiceProvider-1.0.jar>
timeout=10

