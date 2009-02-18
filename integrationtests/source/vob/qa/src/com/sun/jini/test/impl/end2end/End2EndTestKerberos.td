testClass=com.sun.jini.test.impl.end2end.e2etest.Driver
testCategories=end2end,end2end_impl
com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipMacTestVerifier
testjvmargs=-Djava.security.policy=<url:harness/policy/all.policy>
com.sun.jini.test.impl.end2end.properties=e2etest/Driver.properties
com.sun.jini.test.impl.end2end.kinit="${java.home}$/bin$/kinit -f -k -t ${clientKeytabLocation} ${com.sun.jini.qa.harness.kerberos.clientPrincipal}"
end2end.kerberos=true
include com/sun/jini/test/impl/end2end/e2etest/Driver.properties
