testClass=com.sun.jini.test.spec.jeri.transport.kerberos.KerberosServerConstructorAccessorTest
testCategories=jeri,jeri_spec
testConfiguration=<url: KerberosServerEndpointTest.config>

#no cosmetic whitespace
testjvmargs=${testjvmargs},\
-Djava.security.auth.login.config=<url:com/sun/jini/test/spec/jeri/transport/resources/kerberos.login>,\
-Djava.security.krb5.realm=${com.sun.jini.qa.harness.kerberos.realm},\
-Djava.security.krb5.kdc=${com.sun.jini.qa.harness.kerberos.kdc},\
-DclientKeytabLocation=${com.sun.jini.qa.harness.kerberos.aggregatePasswordFile},\
-DserverKeytabLocation=${com.sun.jini.qa.harness.kerberos.aggregatePasswordFile},\
-Djeri.transport.kerberosServer=${com.sun.jini.qa.harness.kerberos.serverPrincipal},\
-Djeri.transport.kerberosClient=${com.sun.jini.qa.harness.kerberos.clientPrincipal}

testPolicyfile=com/sun/jini/test/spec/jeri/transport/resources/kerberos.policy
jeri.transport.wrappedTest=com.sun.jini.test.spec.jeri.transport.ServerEndpointTest
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false

