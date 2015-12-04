testClass=Constructor_Test
testCategories=config,config_spec
testPolicyfile=configurationfile.policy
org.apache.river.qa.harness.runjiniserver=false
org.apache.river.qa.harness.runkitserver=false
org.apache.river.qa.harness.testClassServer=

# Set java.protocol.handler.pkgs system property to net.jini.url
# to enable HTTPMD urls creating
testjvmargs=-Djava.protocol.handler.pkgs=net.jini.url

# HTTP Server parameters
HTTPServer.impl=org.apache.river.tool.ClassServer
HTTPServer.dir=${java.io.tmpdir}
HTTPServer.port=8082
HTTPServer.type=classServer

# HTTP Server parameters
HTTPServer2.impl=org.apache.river.tool.ClassServer
HTTPServer2.dir=${java.io.tmpdir}
HTTPServer2.port=8083
HTTPServer2.type=classServer
