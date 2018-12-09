testClass=org.apache.river.test.spec.jeri.transport.RobustnessTest
#testCategories=jeri,jeri_spec
testConfiguration=<url: SslServerEndpointTest.config>
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
# TODO: This test is disabled as the latest SSL Server Endpoints require 
# client auth and certificates.  Update this test with principal certs and login contexts.
org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier