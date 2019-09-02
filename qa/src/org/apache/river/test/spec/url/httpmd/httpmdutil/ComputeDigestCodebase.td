testClass=ComputeDigestCodebase
testCategories=url_spec,url
# Skip test verifier doesn't work because the file name is too long.
org.apache.river.qa.harness.verifier=org.apache.river.qa.harness.SkipWindowsTestVerifier
# TODO: This test is unable to be run on windows because the command argument is
# too long.  The test needs to be changed to load these properties from
# a properties file after the test jvm starts.

include=computeDigestCodebase.properties