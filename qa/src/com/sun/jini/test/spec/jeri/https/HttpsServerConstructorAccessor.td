testClass=com.sun.jini.test.spec.jeri.transport.ssl.SslServerConstructorAccessorTest
testCategories=jeri,jeri_spec
testConfiguration=<url: HttpsServerEndpointTest.config>
testPolicyfile=com/sun/jini/test/spec/jeri/transport/resources/ssl.policy

# no cosmetic whitespace, parse on ,
testjvmargs=${testjvmargs},\
-Djava.security.auth.login.config=<url:com/sun/jini/test/spec/jeri/transport/resources/ssl.login>,\
-DkeyStoreURL=<url:com/sun/jini/test/spec/jeri/transport/resources/keystore>,\
-DkeyStorePasswordURL=<url:com/sun/jini/test/spec/jeri/transport/resources/keystore.password>

com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
