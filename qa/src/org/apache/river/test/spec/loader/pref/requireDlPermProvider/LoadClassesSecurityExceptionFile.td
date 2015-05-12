testClass=LoadClassesSecurityException
testCategories=loader,loader_spec
testPolicyfile=loaderNoDlPerm.policy
loader.isHttp=false
testjvmargs=-Dnet.jini.loader.ClassLoading.provider=net.jini.loader.pref.RequireDlPermProvider
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
