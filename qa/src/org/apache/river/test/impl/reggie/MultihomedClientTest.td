# Name service provider has been moved to an external
# library to allow compilation on platforms other than Sun JVM.
#org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipNonSunJDKTestVerifier
# This test uses a name service provider, unfortunately this doesn't seem to
# be working properly with Java 8 and IPv6.  From Java 9, this nameservice provider
# has been removed, thus this test cannot run.
org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier
#https://bugs.openjdk.java.net/browse/JDK-8134577
#
#To facilitate testing activity, and provide a replacement for the
#customized NameService implementations in the
#JDK tests, the default NameService has been extended to support
#the retrieval of host to IP address mappings from a file.
#The file path is specified with a system property " jdk.internal.hosts".
#
#Previously a nameservice provider was specified by setting the system
#property
#"sun.net.spi.nameservice.provider.", as per the documentation
#http://docs.oracle.com/javase/8/docs/technotes/guides/net/properties.html

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

