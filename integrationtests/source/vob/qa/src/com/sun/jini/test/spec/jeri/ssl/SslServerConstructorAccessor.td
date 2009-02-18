testClass=com.sun.jini.test.spec.jeri.transport.ssl.SslServerConstructorAccessorTest
testCategories=jeri,jeri_spec
testConfiguration=<url:SslServerEndpointTest.config>

#no white space
testjvmargs=${testjvmargs},\
-Djava.security.auth.login.config=<url:com/sun/jini/test/spec/jeri/transport/resources/ssl.login>,\
-DkeyStoreURL=<url:com/sun/jini/test/spec/jeri/transport/resources/keystore>,\
-DkeyStorePasswordURL=<url:com/sun/jini/test/spec/jeri/transport/resources/keystore.password>

testPolicyfile=com/sun/jini/test/spec/jeri/transport/resources/ssl.policy

com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
