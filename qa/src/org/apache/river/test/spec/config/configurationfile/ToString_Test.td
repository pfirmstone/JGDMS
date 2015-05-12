testClass=ToString_Test
testCategories=config,config_spec
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

