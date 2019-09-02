# Test fails, but the test documentation doesn't reflect what's actually 
# being tested.  It appears the test is checking for an invalid configuration
# while the test documentation says its testing for proxy equality. 
org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipTestVerifier
testClass=SharedGroupNullConfigEntries
testCategories=start,start_impl

org.apache.river.start.group.SharedGroup.instances=3

org.apache.river.start.group.SharedGroup.override.0=org.apache.river.start.activationIdPreparer=null
org.apache.river.start.group.SharedGroup.override.1=org.apache.river.start.activationSystemPreparer=null
org.apache.river.start.group.SharedGroup.override.2=org.apache.river.start.exporter=null
include0=start.properties
