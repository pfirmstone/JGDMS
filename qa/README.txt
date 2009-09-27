===============================================
PLEASE NOTE THAT BELOW INFO IS (VERY) OUTDATED!
It is only kept for reference while updated documentation is under develoment.
Meanwhile, please refer to:
<river home>/build.xml ('qa.run' task) e.g. 'ant qa.run'
<river home>/qa/build.xml ('run-categories', 'run-tests', 'jtreg' tasks) e.g. 'ant jtreg'
===============================================

This file describes the steps one can take to build and run the tests
provided under the 'integrationtests' directory of Apache River. Note
that the steps outlined here assume a unix or linux based system with
access to the GNU make utility.

1. CHECKOUT

     Suppose your home directory is /home/myUsername, and suppose you
     checkout the river distribution (including the QA test source)
     from the Apache River Project as show below,

     > cd ~
     > svn checkout \
           https://svn.apache.org/repos/asf/incubator/river/jtsk/trunk \
           jtsk-trunk

     Note also that there are a number of example commands presented
     in this document that use '~' for the user's home directory.
     In those commands, '~' is allowable. In all other commands
     shown, the absolute path is required.

2. Set - JAVA_HOME

     > export JAVA_HOME=/usr/lib/j2se/jdk1.6.0

     JAVA_HOME must be set as an environment variable. If it is
     not set, then neither the ant scripts used to compile the
     river source, nor the make file used to compile the QA tests,
     will work. In the example command line above, replace the
     value used for the location of the Java home directory 
     with the appropriate value for your system.

3. BUILD - the source

     > cd ~/jtsk-trunk
     > ant clean (optional)
     > ant
     > ant jars

4. INSTALL - the policy provider

     > su [must be root to execute the install.policy target]

     # cd ~/jtsk-trunk
     # ant install.policy
     # ls -al /usr/lib/j2se/jdk1.6.0/jre/lib/ext

     -rw-r--r--  1 root root  31106 2008-05-19 17:42 jsk-policy.jar

     # exit

     -- The Apache River Jini distribution should now be built --

5. BUILD - the qa tests

     > cd ~/jtsk-trunk/integrationtests
     > make clean (optional)
     > make
     > make jars

6. INSTALL - the merged policy provider for the qa tests

     > su [must be root to install this provider]

     # cd ~/jtsk-trunk/integrationtests/qa/lib
     # cp mergedpolicyprovider.jar /usr/lib/j2se/jdk1.6.0/jre/lib/ext
     # exit

     In the example command line above, replace the value 
     used for the location of the Java home directory with
     the appropriate value for your system.

7. RUNNING - the qa tests

     > cd ~/jtsk-trunk/integrationtests/qa/src

     NOTE: elements of the command line below are broken
           up below for readability

     Example 7a. Run individual test(s) in transient mode, named:

     com.sun.jini.test.spec.joinmanager.Register
     com.sun.jini.test.impl.joinmanager.LeaseRenewDurRFE
     com.sun.jini.test.impl.servicediscovery.event.LookupTaskServiceIdMapRace

     > java 
       -cp $HOME/jtsk-trunk/integrationtests/qa/lib/jiniharness.jar
           :$HOME/jtsk-trunk/lib/jsk-platform.jar
           :$HOME/jtsk-trunk/lib/jsk-lib.jar
       -Djava.security.policy=
    $HOME/jtsk-trunk/integrationtests/qa/harness/policy/qa.policy
       -Djava.util.logging.config.file=
    $HOME/jtsk-trunk/integrationtests/qa/src/com/sun/jini/test/resources/qa1.logging
    com.sun.jini.qa.harness.QARunner
    $HOME/jtsk-trunk/integrationtests/qa/src/com/sun/jini/test/resources/qaHarness.prop
    -testJar $HOME/jtsk-trunk/integrationtests/qa/lib/jinitests.jar
    -tests com/sun/jini/test/spec/joinmanager/Register.td,
           com/sun/jini/test/impl/joinmanager/LeaseRenewDurRFE.td,
           com/sun/jini/test/impl/servicediscovery/event/LookupTaskServiceIdMapRace.td
    -com.sun.jini.qa.harness.serviceMode transient

     Example 7b. Run all tests in transient mode under the
                 joinmanager categories:

     > java 
       -cp $HOME/jtsk-trunk/integrationtests/qa/lib/jiniharness.jar
           :$HOME/jtsk-trunk/lib/jsk-platform.jar
           :$HOME/jtsk-trunk/lib/jsk-lib.jar
       -Djava.security.policy=
    $HOME/jtsk-trunk/integrationtests/qa/harness/policy/qa.policy
       -Djava.util.logging.config.file=
    $HOME/jtsk-trunk/integrationtests/qa/src/com/sun/jini/test/resources/qa1.logging
    com.sun.jini.qa.harness.QARunner
    $HOME/jtsk-trunk/integrationtests/qa/src/com/sun/jini/test/resources/qaHarness.prop
    -testJar $HOME/jtsk-trunk/integrationtests/qa/lib/jinitests.jar
    -categories joinmanager 
    -com.sun.jini.qa.harness.serviceMode transient

   Examples of test categories that can be run:

    joinmanager
    lookupdiscoveryspec
    discoveryservice
    fiddler
    servicediscovery
    servicediscoveryevent
    etc.

   MODE:    To run the services being tested in activatable
            mode, rather than transient mode as was shown
            above, simply exclude the argument
            '-com.sun.jini.qa.harness.serviceMode transient'
            from each command line in the examples.

   POLICY:  The system property related to security policy
            is optional when not running the secure test
            configurations.

   LOGGING: The system property related to logging is also
            optional, but if it is excluded, then only minimal
            default logging output will be displayed. In the
            examples above, the logging file,
   $HOME/jtsk-trunk/integrationtests/qa/src/com/sun/jini/test/resources/qa1.logging
            is used to configure logging for both the test
            harness and the tests themselves. Most loggers
            referenced in the qa1.logging configuration file
            are initially configured at the INFO level. If
            you would like to see more verbose logging output,
            then edit qa1.logging and change the level of
            the desired logger to the appropriate level.
