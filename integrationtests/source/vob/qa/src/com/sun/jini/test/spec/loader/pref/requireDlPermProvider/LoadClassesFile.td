testClass=LoadClasses
testCategories=loader,loader_spec
testPolicyfile=loader.policy
loader.isHttp=false
testjvmargs=-Djava.rmi.server.RMIClassLoaderSpi=net.jini.loader.pref.RequireDlPermProvider
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
