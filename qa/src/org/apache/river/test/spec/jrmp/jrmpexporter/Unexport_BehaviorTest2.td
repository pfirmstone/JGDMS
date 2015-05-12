#Test fails randomly due to network socket not releasing fast enough on Linux.
org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier
testClass=Unexport_BehaviorTest
testCategories=jrmp,jrmp_spec
jrmp.util.constructorType=2
jrmp.util.constructorPort=5678
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
