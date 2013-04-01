#Test fails randomly due to network socket not releasing fast enough on Linux.
com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipTestVerifier
testClass=Unexport_BehaviorTest
testCategories=jrmp,jrmp_spec
jrmp.util.constructorType=3
jrmp.util.constructorPort=5678
com.sun.jini.qa.harness.runkitserver=false 
com.sun.jini.qa.harness.runjiniserver=false
