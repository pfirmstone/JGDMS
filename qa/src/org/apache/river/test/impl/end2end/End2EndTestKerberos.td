testClass=org.apache.river.test.impl.end2end.e2etest.Driver
testCategories=end2end,end2end_impl
org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipMacTestVerifier
testjvmargs=-Djava.security.policy=<url:harness/policy/all.policy>
org.apache.river.test.impl.end2end.properties=e2etest/Driver.properties
org.apache.river.test.impl.end2end.kinit="${java.home}$/bin$/kinit -f -k -t ${clientKeytabLocation} ${org.apache.river.qa.harness.kerberos.clientPrincipal}"
end2end.kerberos=true
include org/apache/river/test/impl/end2end/e2etest/Driver.properties
