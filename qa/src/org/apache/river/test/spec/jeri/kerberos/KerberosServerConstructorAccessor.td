testClass=org.apache.river.test.spec.jeri.transport.kerberos.KerberosServerConstructorAccessorTest
testCategories=jeri,jeri_spec
testConfiguration=<url: KerberosServerEndpointTest.config>

#no cosmetic whitespace
testjvmargs=${testjvmargs},\
-Djava.security.auth.login.config=<url:org/apache/river/test/spec/jeri/transport/resources/kerberos.login>,\
-Djava.security.krb5.realm=${org.apache.river.qa.harness.kerberos.realm},\
-Djava.security.krb5.kdc=${org.apache.river.qa.harness.kerberos.kdc},\
-DclientKeytabLocation=${org.apache.river.qa.harness.kerberos.aggregatePasswordFile},\
-DserverKeytabLocation=${org.apache.river.qa.harness.kerberos.aggregatePasswordFile},\
-Djeri.transport.kerberosServer=${org.apache.river.qa.harness.kerberos.serverPrincipal},\
-Djeri.transport.kerberosClient=${org.apache.river.qa.harness.kerberos.clientPrincipal}

testPolicyfile=org/apache/river/test/spec/jeri/transport/resources/kerberos.policy
jeri.transport.wrappedTest=org.apache.river.test.spec.jeri.transport.ServerEndpointTest
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false

org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier