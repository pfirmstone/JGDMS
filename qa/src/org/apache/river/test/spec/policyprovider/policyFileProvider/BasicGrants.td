testClass=BasicGrants
testCategories=policyprovider,policyprovider_spec
testPolicyfile=policyProviderGrant01.policy
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
FILEPOLICY01=<url: policyProviderGrant01.policy>
FILEPOLICY02=<url: policyProviderGrant02.policy>
FILEPOLICYUMBRELLA=<url: policyProviderUmbrellaGrant.policy>
org.apache.river.qa.harness.securityproperties=<url: ./securityprovider.properties>
#testjvmargs=-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}