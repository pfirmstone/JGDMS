testClass=org.apache.river.test.spec.jeri.transport.ssl.SslServerConstructorAccessorTest
testCategories=jeri,jeri_spec
testConfiguration=<url:SslServerEndpointTest.config>

#no white space
testjvmargs=${testjvmargs},\
-Djava.security.auth.login.config=<url:org/apache/river/test/spec/jeri/transport/resources/ssl.login>,\
-DkeyStoreURL=<url:org/apache/river/test/spec/jeri/transport/resources/keystore>,\
-DkeyStorePasswordURL=<url:org/apache/river/test/spec/jeri/transport/resources/keystore.password>

testPolicyfile=org/apache/river/test/spec/jeri/transport/resources/ssl.policy

org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
