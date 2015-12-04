# Test creates a set and registers for both warning and renewail failure
# events.  One listener will throw an exception, the other will succed.
# We the take action that will cause both to listers to recive an event.
# If both recive an event we optionally shutdown the server, and then
# take the action again.  At this point we pass if only one of the
# events are recived (the one with the listener that did not throw
# an exception) and fail otherwise

testClass=RemoveExactlyOneTest
testCategories=renewalservice,renewalservice_impl

org.apache.river.test.impl.norm.tryShutdown=true
org.apache.river.test.impl.norm.failureFails=true
include0=norm.properties
testLoggingProperties=<file: src/org/apache/river/test/resources/qa1_debug.logging>
