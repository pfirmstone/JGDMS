testClass=RefreshNoPrincipal
testCategories=policyprovider,policyprovider_spec
testPolicyfile=policyProviderGrant01.policy
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
FILEPOLICY01=<url: policyProviderGrant01.policy>
FILEPOLICY02=<url: policyProviderGrant02.policy>
FILEPOLICYUMBRELLA=<url: policyProviderUmbrellaGrant.policy>
com.sun.jini.qa.harness.securityproperties=<url: ../securityprovider.properties>
#testjvmargs=-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}
#testjvmargs=-Djava.security.manager=com.sun.jini.tool.ProfilingSecurityManager,${testjvmargs}