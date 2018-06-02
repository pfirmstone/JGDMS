testClass=BadEventCodebaseTest
testCategories=eventmailbox,eventmailbox_impl
#net.jini.loader.ClassLoading.provider=java.rmi.server.RMIClassLoader
#testjvmargs=-Xdebug,\
#-Xrunjdwp:transport=dt_socket+,address=8000+,server=y+,suspend=y,\
#${testjvmargs}
net.jini.event.PullEventMailbox.classpath=${net.jini.event.EventMailbox.classpath}$:<file:lib/qa1-mercury-bogusimpl.jar>

include0=mercury.properties
~

