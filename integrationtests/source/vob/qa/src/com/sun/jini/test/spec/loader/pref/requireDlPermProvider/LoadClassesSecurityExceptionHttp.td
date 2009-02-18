testClass=LoadClassesSecurityException
testCategories=loader,loader_spec
testPolicyfile=loaderNoDlPerm.policy
loader.isHttp=true
loader.httpPort=8087
testjvmargs=-Djava.rmi.server.RMIClassLoaderSpi=net.jini.loader.pref.RequireDlPermProvider
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
