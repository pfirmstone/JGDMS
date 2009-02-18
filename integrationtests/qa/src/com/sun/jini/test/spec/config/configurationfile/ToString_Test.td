testClass=ToString_Test
testCategories=config,config_spec
com.sun.jini.qa.harness.runjiniserver=false
com.sun.jini.qa.harness.runkitserver=false
com.sun.jini.qa.harness.testClassServer=

# Set java.protocol.handler.pkgs system property to net.jini.url
# to enable HTTPMD urls creating
testjvmargs=-Djava.protocol.handler.pkgs=net.jini.url

# HTTP Server parameters
HTTPServer.impl=com.sun.jini.tool.ClassServer
HTTPServer.dir=${java.io.tmpdir}
HTTPServer.port=8082
HTTPServer.type=classServer

