








     This file describes the steps one can take to build and run
     the tests provided under the 'qatests' directory of the 
     river distribution. Note that the steps outlined here assume
     a unix or linux based system with access to the GNU make
     utility.

     Wherever the tokens '<jtskTrunk>' or '<qatestsTrunk>' appear
     in this document, one should take this to represent,
     respectively, the paths of the trunk in the 'jtsk' working
     directory and the 'qatests' working directory under which
     the river distribution has been checked out from subversion.

     For example, consider the directory paths used in the first
     section below, in which source checkout is discussed,

     1. CHECKOUT

     Suppose your home directory is /home/myUsername, and suppose you
     checkout the river distribution (including the QA test source)
     from the Apache River Project as show below,

     > cd ~
     > svn checkout https://svn.apache.org/repos/asf/incubator/river river

     then <jtskTrunk> represents /home/myUsername/river/jtsk/trunk,
     and <qatestsTrunk> rerpresents /home/myUsername/river/qatests/trunk
     in all commands where the corresponding token appears. When
     using those commands in your own environment, each such token
     should be replaced with the analogous and appropriate path for
     your system.

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

     > cd ~/river/jtsk/trunk
     > ant clean (optional)
     > ant
     > ant jars

     NOTE: make sure you run the above command from the top-level
           directory, ~/river/jtsk/trunk, NOT one level lower
           (~/river/jtsk/trunk/src).

     4. INSTALL - the policy provider

     > su [must be root to execute the install.policy target]

     # cd ~/river/jtsk/trunk
     # ant install.policy
     # ls -al /usr/lib/j2se/jdk1.6.0/jre/lib/ext

     -rw-r--r--  1 root root  31106 2008-05-19 17:42 jsk-policy.jar

     # exit

     -- The Apache River Jini distribution should now be built --

     5. BUILD - the qa tests

     > cd ~/river/qatests/trunk/source/vob
     > make clean (optional)
     > make
     > make jars

     6. INSTALL - the merged policy provider for the qa tests

     > su [must be root to install this provider]

     # cd ~/river/qatests/trunk/source/vob/qa/lib
     # cp mergedpolicyprovider.jar /usr/lib/j2se/jdk1.6.0/jre/lib/ext
     # exit

     In the example command line above, replace the value 
     used for the location of the Java home directory with
     the appropriate value for your system.


     7. RUNNING - the qa tests

     > cd ~/river/qatests/trunk/source/vob/qa/src

     NOTE: elements of the command line below are broken
           up below for readability

     Example 7a. Run individual test(s) in transient mode, named:

     com.sun.jini.test.spec.joinmanager.Register
     com.sun.jini.test.impl.joinmanager.LeaseRenewDurRFE
     com.sun.jini.test.impl.servicediscovery.event.LookupTaskServiceIdMapRace

     > java 
       -cp  <qatestsTrunk>/source/vob/qa/lib/jiniharness.jar
           :<jtskTrunk>/lib/jsk-platform.jar
           :<jtskTrunk>/lib/jsk-lib.jar
       -Djava.security.policy=
    <qatestsTrunk>/source/vob/qa/harness/policy/qa.policy
       -Djava.util.logging.config.file=
    <qatestsTrunk>/source/vob/qa/src/com/sun/jini/test/resources/qa1.logging
    com.sun.jini.qa.harness.QARunner
    <qatestsTrunk>/source/vob/qa/src/com/sun/jini/test/resources/qaHarness.prop
    -testJar <qatestsTrunk>/source/vob/qa/lib/jinitests.jar
    -tests com/sun/jini/test/spec/joinmanager/Register.td,
           com/sun/jini/test/impl/joinmanager/LeaseRenewDurRFE.td,
           com/sun/jini/test/impl/servicediscovery/event/LookupTaskServiceIdMapRace.td
    -com.sun.jini.qa.harness.serviceMode transient

     Example 7b. Run all tests in transient mode under the
                 joinmanager categories:

     > java 
       -cp  <qatestsTrunk>/source/vob/qa/lib/jiniharness.jar
           :<jtskTrunk>/lib/jsk-platform.jar
           :<jtskTrunk>/lib/jsk-lib.jar
       -Djava.security.policy=
    <qatestsTrunk>/source/vob/qa/harness/policy/qa.policy
       -Djava.util.logging.config.file=
    <qatestsTrunk>/source/vob/qa/src
                                     /com/sun/jini/test/resources/qa1.logging
    com.sun.jini.qa.harness.QARunner
    <qatestsTrunk>/source/vob/qa/src
                                  /com/sun/jini/test/resources/qaHarness.prop
    -testJar <qatestsTrunk>/source/vob/qa/lib/jinitests.jar
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
   <qatestsTrunk>/source/vob/qa/src/com/sun/jini/test/resources/qa1.logging
            is used to configure logging for both the test
            harness and the tests themselves. Most loggers
            referenced in the qa1.logging configuration file
            are initially configured at the INFO level. If
            you would like to see more verbose logging output,
            then edit qa1.logging and change the level of
            the desired logger to the appropriate level.
