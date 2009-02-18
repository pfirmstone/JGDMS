testClass=LoadClassesClassNotFoundException
testCategories=loader,loader_spec
loader.isHttp=false
loader.requireDlPerm=false
loader.annotator=true
testjvmargs=-Djava.rmi.server.RMIClassLoaderSpi=com.sun.jini.test.spec.loader.util.QATestPreferredClassProvider
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
