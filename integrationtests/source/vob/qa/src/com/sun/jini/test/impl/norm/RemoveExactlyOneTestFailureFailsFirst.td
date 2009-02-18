# Test creates a set and registers for both warning and renewail failure
# events.  One listener will throw an exception, the other will succed.
# We the take action that will cause both to listers to recive an event.
# If both recive an event we optionally shutdown the server, and then
# take the action again.  At this point we pass if only one of the
# events are recived (the one with the listener that did not throw
# an exception) and fail otherwise

testClass=RemoveExactlyOneTest
testCategories=renewalservice,renewalservice_impl

com.sun.jini.test.impl.norm.tryShutdown=true
com.sun.jini.test.impl.norm.failureFails=true
com.sun.jini.test.impl.norm.whenFailure=10000
com.sun.jini.test.impl.norm.whenWarning=30000
include0=norm.properties
