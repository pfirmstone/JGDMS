testClass=IsPreferredResource
testCategories=loader,loader_spec
testPolicyfile=loader.policy
loader.httpPort=8087
loader.requireDlPerm=true
# The next option causes (only since the modular build)
# Error: Could not find or load main class '-Dloader.exportAnnotation=export annotation string may be any string'
#loader.exportAnnotation="export annotation string may be any string"
org.apache.river.qa.harness.runkitserver=false 
org.apache.river.qa.harness.runjiniserver=false
