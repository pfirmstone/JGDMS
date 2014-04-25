testClass=LoadClassesSecurityException
testCategories=loader,loader_spec
testPolicyfile=loaderNoDlPerm.policy
loader.isHttp=false
testjvmargs=-Dnet.jini.loader.ClassLoading.provider=net.jini.loader.pref.RequireDlPermProvider
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
