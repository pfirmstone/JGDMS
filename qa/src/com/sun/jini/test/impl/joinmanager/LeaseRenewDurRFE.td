com.sun.jini.qa.harness.verifier=com.sun.jini.qa.harness.SkipNonSunJDKTestVerifier
testClass=LeaseRenewDurRFE
testCategories=joinmanager,joinmanager_impl

# This test starts and discovers at least 1 activatable
# test service. Therefore, this test requires:
#
#  - 1 Phoenix activation system
#  - 1 lookup service
#
# Note that only the test service is required to be activatable
# for this test; it doesn't matter whether the lookup service
# is transient or activatable. If this file configures the
# test for an activatable lookup service, the harness will
# automatically start an activation system. But for this test,
# the test itself, not the harness, starts the activation
# system (in its setup() method, through the AdminManager).
# Hence, a transient lookup service is configured below for
# all cases except the case where the value of the global
# property, com.sun.jini.qa.harness.serviceMode, is set to
# something other than "transient"; in which case, a verifier
# will cause this test to be skipped.
#
# With respect to the com.sun.jini.qa.harness.serviceMode
# property, the only way to prevent the harness from 
# automatically starting Phoenix (which will conflict with
# this test when it also attempts to start Phoenix) is to 
# set that property's value to "transient". But if that
# property is set to "transient" in this file, the value
# set in this file will override the value for that 
# property in the global properties that are set in 
# qaHarness.prop. This means that when the test suite is
# run under the various non-transient modes, this test
# will still be run in transient mode; which may be 
# confusing when test results are analyzed, and will
# certainly cause this test to be repeated unnecessarily.
# Thus, com.sun.jini.qa.harness.serviceMode is NOT set
# in this file.
#
# Because com.sun.jini.qa.harness.serviceMode cannot be
# overridden from the command line anymore, the way to
# run this test in transient mode is to copy qaHarness.prop
# into a local area, change the value of
# com.sun.jini.qa.harness.serviceMode in that file from
# the default value of "activatable" to "transient", and
# reference the new, temporary file on the command line.
# (Of course, you can always checkout this file and 
# uncomment the following line, but copying/modifying
# qaHarness.prop seems easier.)
#
#com.sun.jini.qa.harness.serviceMode=transient

# Thus, what follows are the configuration values 
# needed to run this test:

# Start 1 tranisent lookup service on port 6060

net.jini.lookup.nLookupServices=1
net.jini.lookup.nAddLookupServices=0
net.jini.core.lookup.ServiceRegistrar.port=6060

# Prevent Phoenix from being started by the harness
# during setup. Set this property in the test itself,
# right before attempting to start Phoenix from
# within the test.
com.sun.jini.qa.harness.runactivation=false

# Needed for the test to run outside of the vob
all.policyFile=<url:harness/policy/all.policy>
service.configFile=<url:harness/configs/${com.sun.jini.qa.harness.configs}/test.config>
trust.kerberos.login=<url:harness/trust/kerberos.login>
trust.jsselogins=<url:harness/trust/jsselogins>
trust.policyProps=<url:harness/trust/dynamic-policy.properties>
trust.truststoreFile=<file:harness/trust/truststore>
trust.truststoreURL=<url:harness/trust/truststore>
trust.aggregateKeytab=<url:harness/trust/aggregateKeytab>

# Include additional default items, (some of which may have
# been overridden above), related to the joinmanager category
include0=joinmanager.properties

com.sun.jini.qa.harness.verifier=${com.sun.jini.qa.harness.verifier} com.sun.jini.test.impl.joinmanager.TransientModeVerifier
