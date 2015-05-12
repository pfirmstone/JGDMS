testClass=LoadClasses
testCategories=loader,loader_spec
testPolicyfile=loader.policy
loader.isHttp=true
loader.httpPort=8087
testjvmargs=-Dnet.jini.loader.ClassLoading.provider=net.jini.loader.pref.RequireDlPermProvider
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
