testClass=org.apache.river.test.spec.jeri.transport.ssl.SslServerConstructorAccessorTest
testCategories=jeri,jeri_spec
testConfiguration=<url: HttpsServerEndpointTest.config>
testPolicyfile=org/apache/river/test/spec/jeri/transport/resources/ssl.policy

# no cosmetic whitespace, parse on ,
testjvmargs=${testjvmargs},\
-Djava.security.auth.login.config=<url:org/apache/river/test/spec/jeri/transport/resources/ssl.login>,\
-DkeyStoreURL=<url:org/apache/river/test/spec/jeri/transport/resources/keystore>,\
-DkeyStorePasswordURL=<url:org/apache/river/test/spec/jeri/transport/resources/keystore.password>

org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
