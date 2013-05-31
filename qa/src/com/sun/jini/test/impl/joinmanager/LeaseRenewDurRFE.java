/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.jini.test.impl.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.joinmanager.AbstractBaseTest;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceDiscoveryManager;

import net.jini.core.discovery.LookupLocator;

import net.jini.core.lease.Lease;

import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;


import com.sun.jini.proxy.ConstrainableProxyUtil;

import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;

import net.jini.security.TrustVerifier;

import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import net.jini.security.proxytrust.TrustEquivalence;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;

import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.rmi.Remote;
import java.rmi.RemoteException;

import java.lang.reflect.Method;

import java.util.ArrayList;


import com.sun.jini.start.ServiceProxyAccessor;
import com.sun.jini.start.SharedActivationGroupDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor.Created;
import com.sun.jini.start.SharedGroup;

import com.sun.jini.config.Config;
import com.sun.jini.config.ConfigUtil;
import com.sun.jini.logging.Levels;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.start.Starter;

import net.jini.activation.ActivationExporter;
import net.jini.activation.ActivationGroup;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.proxytrust.ServerProxyTrust;

import net.jini.url.httpmd.HttpmdUtil;

import java.io.File;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.ActivationException;
import java.rmi.MarshalledObject;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.StringTokenizer;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

/**
 * This class verifies that the current implementation of the 
 * <code>JoinManager</code> utility class has correctly implemented 
 * the following RFE:
 * 6202650 - JoinManager should allow configurable control of the
 *           lease renewal interval
 */
public class LeaseRenewDurRFE extends AbstractBaseTest {

    private final static String OVERRIDE_COMPONENT_NAME = "test";
    private final static String SHARED_VM_DIR_PREFIX = 
                                  "LeaseRenewDurRFE-sharedVMDir-TestService_";

    protected final static long SERVICE_ID_VERSION = (0x1L << 12);
    protected final static long SERVICE_ID_VARIANT = (0x2L << 62);

    private static String sep    = System.getProperty("file.separator");
    private static String pSep   = System.getProperty("path.separator");
    private static String urlSep = "/";

    private LookupLocator[] locs = new LookupLocator[0];
    private LookupDiscoveryManager ldm = null;
    private ServiceRegistrar lus = null;
    private ServiceDiscoveryManager sdm = null;

    private long rfeDur   = 15*1000;//short renewal duration to test RFE
    private long rfeDelta = 10;//max extra seconds to wait expiration

    private int nTestServices = 2;
    private ServiceID[] srvcID = new ServiceID[nTestServices];
    private ServiceTemplate[] tmpl = new ServiceTemplate[nTestServices];
    private long[] leaseDur = new long[nTestServices];
    private SharedGroup[] vmProxy = new SharedGroup[nTestServices];

    /* Convenience variables shared by various methods */
    private String host;
    private String jiniPort;
    private String qaPort;

    private String qaHome;
    private String qaHarnessLib;
    private String qaTestHome;
    private String qaTestLib;

    private String qaTest;
    private String qaSpec;
    private String qaImpl;

    private String policyAll;

    private String qaSpecJoinManager;
    private String qaImplJoinManager;

    private String jiniHome;
    private String jiniLib;
    private String jiniLibDL;

    private String proto;
    private static boolean secureProto;

    private String loggingFile;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> start 1 transient lookup service with locator listening on
     *          a specific port
     *     <li> start 1 activation system using all the defaults (ports, etc.)
     *     <li> use locator discovery to discover the started lookup service
     *     <li> verify the lookup is discovered
     *     <li> retrieve the proxy to the lookup that was started
     *     <li> register a simple service with the lookup service, asking
     *          for a 'forever' lease (so that the lookup service will
     *          return its default maximum lease expiration)
     *     <li> retrieve the lease expiration from the registration
     *          and convert it to a duration relative to the current time
     *     <li> cancel the lease on the simple service
     *     <li> store the 'short' lease duration to be tested, as well as
     *           the the default maximum lease duration returned by the
     *          lookup service, for retrieval in the run() method
     *     <li> create identifying service IDs for all test services
     *     <li> set the basic info the service starter framework needs to
     *          start the test services
     *     <li> set the config info needed by the service starter framework
     *          itself
     *     <li> set the config info for the separate shared VMs to be created
     *     <li> set the common service descriptor info that is shared by
     *          each no-op service to be created and run in the associated
     *          shared VM, and which will be used to destroy that shared VM
     *     <li> set the common service descriptor info that is shared by
     *          each TestService to be created and run in the associated
     *          shared VM (for each TestService, a different shared VM
     *          will be created in which the TestService along with a no-op
     *          service will be run)
     *     <li> set the config override(s) that are common to all the
     *          TestService(s) (locators to discover and join)
     * <p>  
     * For each separate TestService to start, do the following:
     * <p><ul>
     *     <li> set the service-specific config overrides - locators to join,
     *          service ID, service value (0, 1, 2, ...), lease duration to
     *          request
     *     <li> set the name of the sharedVM log directory the service starter
     *          should use for the shared VM in which the current TestService
     *          will run; this name should be different for each TestService
     *     <li> determine if the sharedVM log directory exists, and if it
     *          does, remove it so that the service starter can start the
     *          associated TestService
     *     <li> create the activation group (shared VM) in which the
     *          associated no-op service and corresponding TestService
     *          will run
     *     <li> start a no-op service in the shared VM just created; retrieve
     *          and store the resulting service proxy
     *     <li> start a TestService with associated lease duration ('short',
     *          default, etc.) in same shared VM as the corresponding no-op
     *          service just started
     *   </ul>
     * <p>  
     * Create a ServiceDiscoveryManager that will discover the same lookup
     * service(s) as the TestService(s) just started, and which can be
     * accessed by the run() method.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        System.setProperty("com.sun.jini.qa.harness.runactivation", "true");
        getManager().startService("activationSystem");
        /* set shared convenience variables */
        host = sysConfig.getLocalHostName();
        jiniPort = sysConfig.getStringConfigVal("com.sun.jini.jsk.port",
                                                "8080");
        qaPort = sysConfig.getStringConfigVal("com.sun.jini.qa.port", "8081");

        qaHome = sysConfig.getStringConfigVal("com.sun.jini.qa.home",
                                              "/vob/qa");
        qaHarnessLib = qaHome+sep+"lib";
        qaTestHome = sysConfig.getStringConfigVal("com.sun.jini.test.home",
                                                  "/vob/qa");
        qaTestLib = qaTestHome+sep+"lib";

        qaTest =qaTestHome+sep+"src"+sep+"com"+sep+"sun"+sep+"jini"+sep+"test";
        qaSpec = qaTest+sep+"spec";
        qaImpl = qaTest+sep+"impl";

        policyAll = sysConfig.getStringConfigVal
                                    ("all.policyFile",
                                      qaImpl+sep+"start"+sep+"policy.all" );
        logger.log(Levels.HANDLED,"policyFile = "+policyAll);

        jiniHome = sysConfig.getStringConfigVal("com.sun.jini.jsk.home",
                                                "/vob/jive");
        jiniLib   = jiniHome+sep+"lib";
        jiniLibDL = jiniHome+sep+"lib-dl";

        proto = sysConfig.getStringConfigVal
                                  ("com.sun.jini.qa.harness.configs", "jeri");
        if(    (proto.compareToIgnoreCase("jeri") == 0)
            || (proto.compareToIgnoreCase("jrmp") == 0)
            || (proto.compareToIgnoreCase("http") == 0)
            || (proto.compareToIgnoreCase("none") == 0) )
        {
            secureProto = false;
        } else {
            secureProto = true;
        }//endif

        loggingFile = sysConfig.getStringConfigVal
                   ("java.util.logging.config.file","/vob/qa/src/qa1.logging");
        /* lookup discovery */
        List lookupsStarted = getLookupServices().getLookupsStarted();
        LookupLocator[] locs = toLocatorArray(lookupsStarted);
        mainListener.setLookupsToDiscover(lookupsStarted, locs);
        ldm = getLookupDiscoveryManager(locs);
        waitForDiscovery(mainListener);//won't get past here if discovery fails
        /* lookup service */
        ServiceRegistrar lus = (ldm.getRegistrars())[0];
        /* default maximum lease duration from the lookup service */
        int idSeed  = SERVICE_BASE_VALUE + 9999;
        // deliberate, not a bug.
        long lowBits = (1000+idSeed) >> 32;
        long leastSignificantBits = SERVICE_ID_VARIANT | lowBits;
        ServiceID testID =
                    new ServiceID( SERVICE_ID_VERSION, leastSignificantBits );
        ServiceRegistration srvcReg =
                      lus.register(new ServiceItem(testID,testService,null),
                                   Long.MAX_VALUE);
        Lease srvcLease = null;
        srvcReg = (ServiceRegistration) getConfig().prepare
                                    ("test.reggieServiceRegistrationPreparer",
                                     srvcReg);
        srvcLease = (Lease) getConfig().prepare
                                    ("test.reggieServiceRegistrationPreparer",
                                     srvcReg.getLease());
        long defDur = srvcLease.getExpiration() - System.currentTimeMillis();
        srvcLease.cancel();
        /* lease durations to test */
        leaseDur[0] = rfeDur;
        leaseDur[1] = defDur;
        /* test service ids */
        for(int i=0;i<srvcID.length;i++) {
            idSeed  = SERVICE_BASE_VALUE + i;
            // deliberate not a bug.
            lowBits = (1000+idSeed) >> 32;
            leastSignificantBits = SERVICE_ID_VARIANT | lowBits;
            srvcID[i] =
                    new ServiceID( SERVICE_ID_VERSION, leastSignificantBits );
            tmpl[i] = new ServiceTemplate(srvcID[i],null,null);
        }//end loop
        /* service starter file */
        String starterConfigFile = 
         ( (proto.compareToIgnoreCase("none") == 0) ? "-" :
             sysConfig.getStringConfigVal
                                    ("sharedGroup.starterConfiguration","-") );
        String[] starterConfigOptions = new String[] { starterConfigFile };
        Configuration starterConfig = ConfigurationProvider.getInstance
                                       ( starterConfigOptions,
                                         (this.getClass()).getClassLoader() );
        /* shared VM in which the service(s) will run */
        String sharedVMPolicyFile = policyAll;
        String sharedVMClasspath = sysConfig.getStringConfigVal
                                               ("sharedGroup.classpath",null);
        String   serverCommand    = null;
        String[] serverOptions    = null;
        String[] serverProperties = sharedVMProperties(sysConfig);
        /* common no-op service descriptor info (for destroying the VMs */
        String noopServiceCodebase = groupCodebase();
        logger.log(Levels.HANDLED,"noopServiceCodebase = "
                                   +noopServiceCodebase);
        String noopServicePolicyFile = policyAll;
        String noopServiceClasspath = sysConfig.getStringConfigVal
                                          ("sharedGroupImpl.classpath",null);
        logger.log(Levels.HANDLED,"noopServiceClasspath = "
                                   +noopServiceClasspath);
        String noopServiceImplName = sysConfig.getStringConfigVal
                                              ("sharedGroupImpl.impl",null);
        String[] noopServiceArgsArray =  new String[] { starterConfigFile };
        /* common service descriptor info shared by each TestService  */
        String serviceCodebase = serviceCodebase();
        logger.log(Levels.HANDLED,"serviceCodebase = "+serviceCodebase);
        String servicePolicyFile = policyAll;
        String serviceClasspath  = qaHarnessLib+sep+"jiniharness.jar"+pSep
                                   +jiniLib+sep+"jsk-platform.jar"+pSep
                                   +jiniLib+sep+"jsk-lib.jar"+pSep
                                   +qaTestLib+sep+"jinitests.jar";
        logger.log(Levels.HANDLED,"serviceClasspath = "+serviceClasspath);
        String serviceImplName   = "com.sun.jini.test.impl.joinmanager."
                                   +"LeaseRenewDurRFE$RemoteTestServiceImpl";
        /* If com.sun.jini.qa.harness.configs (proto) is "none" then use only
         * the overrides; otherwise, use the standard test config file, and
         * override anything that file doesn't "know" about.
         */
        String serviceConfig = ( (proto.compareToIgnoreCase("none") == 0) ?
              "-" : sysConfig.getStringConfigVal("service.configFile","-") );
        logger.log(Levels.HANDLED,"serviceConfig = "+serviceConfig);

        /* service configuration overrides */
        String locConfigOverride = overrideLocsStr(locs);
        String[] serviceArgs0 = new String[nTestServices];
        String[] serviceArgs1 = new String[nTestServices];
        String[] serviceArgs2 = new String[nTestServices];
        String[] serviceArgs3 = new String[nTestServices];
        for(int v=0; v<nTestServices; v++) {
            serviceArgs0[v] = locConfigOverride;//common override
            /* service-specific overrides - TestService-v */
            serviceArgs1[v] = OVERRIDE_COMPONENT_NAME+".serviceID="
                              +"\""+srvcID[v].toString()+"\"";
            serviceArgs2[v] = OVERRIDE_COMPONENT_NAME+".val="
                              +String.valueOf(v);
            serviceArgs3[v] = "net.jini.lookup.JoinManager.maxLeaseDuration="
                              +String.valueOf(leaseDur[v]);
            /* log service overrides - TestService-v */
            logger.log(Levels.HANDLED,
                 "  *******************************************************");
            logger.log(Levels.HANDLED,
                       "  ******** SETUP Config Overrides: TestService-"
                       +v+" ********");
            logger.log(Levels.HANDLED,"  "+serviceArgs0[v]);
            logger.log(Levels.HANDLED,"  "+serviceArgs1[v]);
            logger.log(Levels.HANDLED,"  "+serviceArgs2[v]);
            logger.log(Levels.HANDLED,"  "+serviceArgs3[v]);
            logger.log(Levels.HANDLED,
                 "  *******************************************************");
            /* config and overrides for service descriptor - TestService-v */
            String[] serviceArgsArray; 
            if(leaseDur[v] == defDur) {//don't configure maxLeaseDuration
                serviceArgsArray =  new String[] { serviceConfig, 
                                                   serviceArgs0[v],
                                                   serviceArgs1[v],
                                                   serviceArgs2[v]
                                                 };
            } else {//configure maxLeaseDuration with value from above
                serviceArgsArray =  new String[] { serviceConfig, 
                                                   serviceArgs0[v],
                                                   serviceArgs1[v],
                                                   serviceArgs2[v],
                                                   serviceArgs3[v] 
                                                 };
            }//endif
            /* can't start service if sharedVM log dir exists from prior run */
            String sharedVMDir = SHARED_VM_DIR_PREFIX+String.valueOf(v);
            File sharedVMDirFD = new File(sharedVMDir);
            if( sharedVMDirFD.exists() ) {
                File[] files = sharedVMDirFD.listFiles();
                for(int i=0; i<files.length; i++) {
                    files[i].delete();
                }//endif
                sharedVMDirFD.delete();
            }//endif
            /* create activation group for no-op service & TestService-v */
            SharedActivationGroupDescriptor sharedActivationGroupDescriptor =
                   new SharedActivationGroupDescriptor(sharedVMPolicyFile,
                                                       sharedVMClasspath,
                                                       sharedVMDir,
                                                       serverCommand,
                                                       serverOptions,
                                                       serverProperties);
            sharedActivationGroupDescriptor.create(starterConfig);
            /* start no-op service in the shared VM just created */
            SharedActivatableServiceDescriptor destroyVMServiceDescriptor =
                new SharedActivatableServiceDescriptor(noopServiceCodebase,
                                                       noopServicePolicyFile,
                                                       noopServiceClasspath,
                                                       noopServiceImplName,
                                                       sharedVMDir,
                                                       noopServiceArgsArray,
                                                       false);
            Created createdObj = (Created)destroyVMServiceDescriptor.create
                                                              (starterConfig);
            vmProxy[v] = (SharedGroup)createdObj.proxy;//to destroy service VM
            /* start TestService-v in same shared VM as no-op service */
            SharedActivatableServiceDescriptor serviceDescriptor =
                new SharedActivatableServiceDescriptor(serviceCodebase,
                                                       servicePolicyFile,
                                                       serviceClasspath,
                                                       serviceImplName,
                                                       sharedVMDir,
                                                       serviceArgsArray,
                                                       false);
            serviceDescriptor.create(starterConfig);
        }//end loop

        /* create SDM to retrieve ref to TestService-i from lookup */
        sdm = new ServiceDiscoveryManager
                                    (ldm, null, sysConfig.getConfiguration());
        return this;
    }//end construct

    /** For each separate TestService started during construct, do the following:
     * <p><ul>
     *     <li> using a blocking lookup method on the service discovery
     *          manager, discover by service ID, the associated TestService;
     *          which will verify that the TestService was started correctly
     *          and has registered with the lookup service as expected
     *     <li> using the proxy to the corresponding no-op service, destroy
     *          the shared VM in which the TestService (and no-op service)
     *          is running; which simulates "pulling the plug" on the 
     *          TestService
     *     <li> using a non-blocking lookup method on the SDM, query the
     *          lookup service to verify that the TestService is still
     *          registered with the lookup service; that is, verify that
     *          the lease has not expired yet
     *     <li> wait a short amount of time past the time of expected
     *          expiration
     *     <li> using a non-blocking lookup method on the SDM, query the
     *          lookup service to verify that the TestService is no longer
     *          registered with the lookup service; that is, verify that
     *          the lease has expired within the expected time frame, which
     *          verifies that the JoinManager used the maximum lease duration
     *          value that was configured
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Levels.HANDLED, "run()");
        long blockMS = 3*60*1000;
        try {
            for( int v=0; v<nTestServices; v++) {
                ServiceItem srvcItem = sdm.lookup(tmpl[v],null,blockMS);
                TestServiceInterface srvcProxy = 
                                      (TestServiceInterface)srvcItem.service;
                long renewDur = srvcProxy.getRenewDur();
                if(renewDur == Lease.FOREVER) {
                    logger.log(Levels.HANDLED,"  TestService-"
                               +srvcProxy.getVal()
                               +": lease duration = DEFAULT");
                } else {
                    logger.log(Levels.HANDLED,"  TestService-"
                               +srvcProxy.getVal()
                               +": lease duration = "+renewDur);
                }//endif
                logger.log(Levels.HANDLED,"  TestService-"+srvcProxy.getVal()
                                      +": ***** 'KILL' service "+v+" *****");
                vmProxy[v].destroyVM(); /* "pull the plug" on the service */
                //srvcProxy.exitService(); /* make service crash on its own */

                /* verify lease expires when expected */
                if(sdm.lookup(tmpl[v],null) == null) {//no blocking this time
                    throw new TestException("TestService-"+v+": lease expired "
                                            +"before expected time ("
                                            +leaseDur[v]+")");
                }//endif
                /* Wait (dur+delta) for lease to expire */
                logger.log(Levels.HANDLED,"  TestService-"+v
                           +": wait at most ("+(leaseDur[v]/1000)+"+"+rfeDelta
                           +") secs for lease to expire");
                /* First wait the lease duration */
                try{ 
                    Thread.sleep(leaseDur[v]);
                } catch(InterruptedException e) { }
                /* Wait a delta amount to account for communication latency */
                boolean leaseExpired = false;
                int i = 0;
                while( !leaseExpired && (i < rfeDelta) ) {
                    try{ Thread.sleep(1000); } catch(InterruptedException e) {}
                    i = i+1;
                    if(sdm.lookup(tmpl[v],null) == null) leaseExpired = true;
                }//end loop
                if(leaseExpired) {
                    logger.log(Levels.HANDLED,"  TestService-"+v
                               +": lease expired after ("+(leaseDur[v]/1000)
                               +"+"+i+") secs");
                } else {
                    throw new TestException("TestService-"+v+": lease did not "
                                            +"expire when expected (dur = "
                                            +leaseDur[v]+")");
                }//endif
            }//end loop
        } finally {
            for(int v=0; v<nTestServices; v++) {
                String sharedVMDir = SHARED_VM_DIR_PREFIX+String.valueOf(v);
                File sharedVMDirFD = new File(sharedVMDir);
                if( sharedVMDirFD.exists() ) {
                    File[] files = sharedVMDirFD.listFiles();
                    for(int i=0; i<files.length; i++) {
                        files[i].delete();
                    }//endif
                    sharedVMDirFD.delete();
                    logger.log(Levels.HANDLED,"  TestService-"+v
                               +": cleanup - removed shared VM dir ("
                               +sharedVMDir+")");
                }//endif
            }//end loop
        }
    }//end run

    private String[] sharedVMProperties(QAConfig sysConfig) throws Exception {
        ArrayList propsList = new ArrayList(43);
        /* miscellaneous items used in all configs */
        propsList.add("com.sun.jini.qa.home");
        propsList.add(qaHome);

	propsList.add("com.sun.jini.qa.harness.harnessJar");
	propsList.add(sysConfig.getStringConfigVal(
					    "com.sun.jini.qa.harness.harnessJar", 
					    null));

	propsList.add("com.sun.jini.qa.harness.testJar");
	propsList.add(sysConfig.getStringConfigVal(
					    "com.sun.jini.qa.harness.testJar",
					    null));

        String debugVal = "off";
        //String debugVal = "access,failure";
        //String debugVal = "policy,access,failure";

        propsList.add("java.security.debug");
        propsList.add(debugVal);

        propsList.add("java.util.logging.config.file");
        propsList.add(loggingFile);

        propsList.add("java.protocol.handler.pkgs");
        propsList.add("net.jini.url");

        /* items used in all secure configs: jsse, https, kerberos */
        if(    (proto.compareToIgnoreCase("jsse") == 0)
            || (proto.compareToIgnoreCase("https") == 0)
            || (proto.compareToIgnoreCase("kerberos") == 0) )
        {
            String loginConfigProp = ( (proto.compareToIgnoreCase("kerberos") == 0)
                                    ? "trust.kerberos.login" : "trust.jsselogins" );

            String loginConfigKey = "java.security.auth.login.config";
            String loginConfigVal = 
		sysConfig.getStringConfigVal(loginConfigProp, null);
	    if (loginConfigVal == null) {
		throw new TestException(loginConfigProp + " is undefined");
	    }
            logger.log(Levels.HANDLED, loginConfigKey+"="+loginConfigVal);
            propsList.add(loginConfigKey);
            propsList.add(loginConfigVal);

            String secPropsKey = "java.security.properties";
            String secPropsVal = 
		sysConfig.getStringConfigVal("trust.policyProps", null);
	    if (secPropsVal == null) {
		throw new TestException("trust.policyProps is undefined");
	    }
            logger.log(Levels.HANDLED, secPropsKey+"="+secPropsVal);
            propsList.add(secPropsKey);
            propsList.add(secPropsVal);
        }//endif

        /* items used in some secure configs: either jsse/https, or kerberos */
        if(    (proto.compareToIgnoreCase("jsse") == 0)
            || (proto.compareToIgnoreCase("https") == 0) )
        {
            /* NOTE: the value returned for trust.truststoreFile that is 
	     *       assigned to javax.net.ssl.trustStore must be a file
             *       because that property does NOT allow URL format;
             *       neither "jar:file:" nor "file:" URLs. If this test
             *       encounters an UnsupportedConstraintException, then
             *       this is an indication of a problem with this property.
             *       So make sure the value is a simple path to the file.
             */
            String trustStoreKey = "javax.net.ssl.trustStore";
            String trustStoreVal = 
		sysConfig.getStringConfigVal("trust.truststoreFile", null);
	    if (trustStoreVal == null) {
		throw new TestException("trust.truststoreFile is undefined");
	    }
            logger.log(Levels.HANDLED, trustStoreKey+"="+trustStoreVal);
            propsList.add(trustStoreKey);
            propsList.add(trustStoreVal);

        } else if(proto.compareToIgnoreCase("kerberos") == 0) {

            String realmKey = "java.security.krb5.realm";
            String realmVal =
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.realm", null);
            logger.log(Levels.HANDLED, realmKey+"="+realmVal);
            propsList.add(realmKey);
            propsList.add(realmVal);

            String kdcKey = "java.security.krb5.kdc";
            String kdcVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.kdc", null);
            logger.log(Levels.HANDLED, kdcKey+"="+kdcVal);
            propsList.add(kdcKey);
            propsList.add(kdcVal);

            String keyTabKey = "keytab";
            String keyTabVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.aggregatePasswordFile", null);
            logger.log(Levels.HANDLED, keyTabKey+"="+keyTabVal);
            propsList.add(keyTabKey);
            propsList.add(keyTabVal);

            String phoenixKey = "phoenix";
            String phoenixVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.phoenixPrincipal", null);
            logger.log(Levels.HANDLED, phoenixKey+"="+phoenixVal);
            propsList.add(phoenixKey);
            propsList.add(phoenixVal);

            String groupKey = "group";
            String groupVal = 	       
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.groupPrincipal", null);

            logger.log(Levels.HANDLED, groupKey+"="+groupVal);
            propsList.add(groupKey);
            propsList.add(groupVal);

            String reggieKey = "reggie";
            String reggieVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.reggiePrincipal", null);
            logger.log(Levels.HANDLED, reggieKey+"="+reggieVal);
            propsList.add(reggieKey);
            propsList.add(reggieVal);

            String mahaloKey = "mahalo";
            String mahaloVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.mahaloPrincipal", null);
            logger.log(Levels.HANDLED, mahaloKey+"="+mahaloVal);
            propsList.add(mahaloKey);
            propsList.add(mahaloVal);

            String outriggerKey = "outrigger";
            String outriggerVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.outriggerPrincipal", null);
            logger.log(Levels.HANDLED, outriggerKey+"="+outriggerVal);
            propsList.add(outriggerKey);
            propsList.add(outriggerVal);

            String mercuryKey = "mercury";
            String mercuryVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.mercuryPrincipal", null);
            logger.log(Levels.HANDLED, mercuryKey+"="+mercuryVal);
            propsList.add(mercuryKey);
            propsList.add(mercuryVal);

            String normKey = "norm";
            String normVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.normPrincipal", null);
            logger.log(Levels.HANDLED, normKey+"="+normVal);
            propsList.add(normKey);
            propsList.add(normVal);

            String fiddlerKey = "fiddler";
            String fiddlerVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.fiddlerPrincipal", null);
            logger.log(Levels.HANDLED, fiddlerKey+"="+fiddlerVal);
            propsList.add(fiddlerKey);
            propsList.add(fiddlerVal);

            String testKey = "test";
            String testVal = 
		sysConfig.getStringConfigVal("com.sun.jini.qa.harness.kerberos.testPrincipal", null);
            logger.log(Levels.HANDLED, testKey+"="+testVal);
            propsList.add(testKey);
            propsList.add(testVal);

            String subjectCredsKey = "javax.security.auth.useSubjectCredsOnly";
            String subjectCredsVal = "false";
            logger.log(Levels.HANDLED, subjectCredsKey+"="+subjectCredsVal);
            propsList.add(subjectCredsKey);
            propsList.add(subjectCredsVal);

        }//endif
        return ((String[])(propsList).toArray(new String[propsList.size()]));
    }//end sharedVMProperties

    private String groupCodebase() throws Exception {
        String codebase = "http://"+host+":"+jiniPort+"/group-dl.jar";
        if(secureProto) {
            codebase = HttpmdUtil.computeDigestCodebase
                         (jiniLibDL, 
                          "httpmd://"+host+":"+jiniPort+"/group-dl.jar;sha=0");
        }//endif
        return codebase;
    }//end groupCodebase

    private String serviceCodebase() throws Exception {
        String serviceCodebase =
                          "http://"+host+":"+qaPort+"/qa1-joinmanager-dl.jar "
                         +"http://"+host+":"+jiniPort+"/jsk-dl.jar";
        if(secureProto) {
            String qaPart = HttpmdUtil.computeDigestCodebase
                (qaTestLib, 
                 "httpmd://"+host+":"+qaPort+"/qa1-joinmanager-dl.jar;sha=0");
            String jiniPart = HttpmdUtil.computeDigestCodebase
                          (jiniLibDL, 
                           "httpmd://"+host+":"+jiniPort+"/jsk-dl.jar;sha=0");
            serviceCodebase = qaPart+" "+jiniPart;
        }//endif
        return serviceCodebase;
    }//end serviceCodebase

    private String[] getDirs(String path) {
        String[] strArray = new String[0];
        String delimiter = "/\\"; // handle mixed path separators
        StringTokenizer st = new StringTokenizer(path,delimiter);
        int n = st.countTokens();
        if (n > 0) {
            strArray = new String[n];
            for(int i=0;((st.hasMoreTokens())&&(i<n));i++) {
                strArray[i] = st.nextToken();
            }
            return strArray;
        } else {
            return strArray;
        }
    }//end getDirs

    private String overrideLocsStr(LookupLocator[] locators) throws TestException {
        String[] locStrs = null;

        String truststore      = null;
        String principalReggie = null;
        String principalTester = null;
        String locConstraints  = null;
        boolean setConstraints = false;

        if(secureProto) {
	    String truststoreURL=
		config.getStringConfigVal("trust.truststoreURL", null);
	    if (truststoreURL == null) {
		throw new TestException("trust.truststoreURL is undefined");
	    }
            setConstraints = true;
            truststore = "com.sun.jini.config.KeyStores.getKeyStore"
                          +"("
                          +"\""
                          + truststoreURL
                          +"\""
                          +",null"
                          +")"; 
            logger.log(Levels.HANDLED,"  truststore = "+truststore);
        }//endif

        /* set protocol-specific locator constraints */
        if(    (proto.compareToIgnoreCase("jsse")  == 0)
            || (proto.compareToIgnoreCase("https") == 0) )
        {
            principalReggie = "com.sun.jini.config.KeyStores.getX500Principal"
                                    +"("+"\""+"reggie"+"\""+","+truststore+")";
            principalTester = "com.sun.jini.config.KeyStores.getX500Principal"
                                    +"("+"\""+"tester"+"\""+","+truststore+")";
            /* Note: the non-unicast constraints below are not necessary
             *       because all discovery is done using only unicast; but
             *       are included for reference.
             */
            locConstraints = 
            "new BasicMethodConstraints"
            +"("
                +"new MethodDesc[]"
                +"{ "

                  +"new MethodDesc"
                  +"("
                      +"\""+"multicastRequest"+"\""+","
                      +"new InvocationConstraints"
                      +"("
                          +"new InvocationConstraint[]"
                          +"{"
                              +"ClientAuthentication.YES,"
                              +"new ClientMinPrincipal("+principalTester+"),"
                              +"Integrity.YES,"
                              +"DiscoveryProtocolVersion.TWO,"
                              +"new MulticastMaxPacketSize(1024),"
                              +"new MulticastTimeToLive(0),"
                              +"new UnicastSocketTimeout(120000)"
                          +"},"
                          +"null"
                      +")"
                  +")"
                  +","

                  +"new MethodDesc"
                  +"("
                      +"\""+"multicastAnnouncement"+"\""+","
                      +"new InvocationConstraints"
                      +"("
                          +"new InvocationConstraint[]"
                          +"{"
                              +"Integrity.YES,"
                              +"ServerAuthentication.YES,"
                              +"new ServerMinPrincipal("+principalReggie+"),"
                              +"DiscoveryProtocolVersion.TWO,"
                              +"new MulticastMaxPacketSize(1024),"
                              +"new MulticastTimeToLive(0),"
                              +"new UnicastSocketTimeout(120000)"
                          +"},"
                          +"null"
                      +")"
                  +")"
                  +","

                  +"new MethodDesc"
                  +"("
                      +"\""+"unicastDiscovery"+"\""+","
                      +"new InvocationConstraints"
                      +"("
                          +"new InvocationConstraint[]"
                          +"{"
                              +"Integrity.YES,"
                              +"ServerAuthentication.YES,"
                              +"new ServerMinPrincipal("+principalReggie+"),"
                              +"DiscoveryProtocolVersion.TWO,"
                              +"new MulticastMaxPacketSize(1024),"
                              +"new MulticastTimeToLive(0),"
                              +"new UnicastSocketTimeout(120000)"
                          +"},"
                          +"null"
                      +")"
                  +")"
                 +","

                  +"new MethodDesc"
                  +"("
                      +"\""+"getRegistrar"+"\""+","
                      +"new InvocationConstraints"
                      +"("
                          +"new InvocationConstraint[]"
                          +"{"
                              +"Integrity.YES,"
                              +"ServerAuthentication.YES,"
                              +"new ServerMinPrincipal("+principalReggie+"),"
                              +"DiscoveryProtocolVersion.TWO,"
                              +"new MulticastMaxPacketSize(1024),"
                              +"new MulticastTimeToLive(0),"
                              +"new UnicastSocketTimeout(120000)"
                          +"},"
                          +"null"
                      +")"
                  +")"

                +"}"
            +")";

        } else if(proto.compareToIgnoreCase("kerberos") == 0) {
	    String pName = config.getStringConfigVal("com.sun.jini.qa.harness.kerberos.reggiePrincipal", null);
            principalReggie = 
                  "new javax.security.auth.kerberos.KerberosPrincipal"
                   +"("
                       +"\""+pName+"\""
                   +")";
	    pName = config.getStringConfigVal("com.sun.jini.qa.harness.kerberos.testPrincipal", null);
            principalTester = 
                  "new javax.security.auth.kerberos.KerberosPrincipal"
                   +"("
                       +"\""+pName+"\""
                   +")";
            locConstraints = 
            "new BasicMethodConstraints"
            +"("
                +"new MethodDesc[]"
                +"{ "

                  +"new MethodDesc"
                  +"("
                      +"\""+"unicastDiscovery"+"\""+","
                      +"new InvocationConstraints"
                      +"("
                          +"new InvocationConstraint[]"
                          +"{"
                              +"Integrity.YES,"
                              +"ServerAuthentication.YES,"
                              +"new ServerMinPrincipal("+principalReggie+"),"
                              +"DiscoveryProtocolVersion.TWO,"
                              +"new MulticastMaxPacketSize(1024),"
                              +"new MulticastTimeToLive(0),"
                              +"new UnicastSocketTimeout(120000)"
                          +"},"
                          +"null"
                      +")"
                  +")"
                  +","

                  +"new MethodDesc"
                  +"("
                      +"\""+"getRegistrar"+"\""+","
                      +"new InvocationConstraints"
                      +"("
                          +"new InvocationConstraint[]"
                          +"{"
                              +"Integrity.YES,"
                              +"ServerAuthentication.YES,"
                              +"new ServerMinPrincipal("+principalReggie+"),"
                              +"DiscoveryProtocolVersion.TWO,"
                              +"new MulticastMaxPacketSize(1024),"
                              +"new MulticastTimeToLive(0),"
                              +"new UnicastSocketTimeout(120000)"
                          +"},"
                          +"null"
                      +")"
                  +")"
                  +","

                  +"new MethodDesc"
                  +"("
                      +"new InvocationConstraints"
                      +"("
                          +"new InvocationConstraint[]"
                          +"{"
                              +"DiscoveryProtocolVersion.TWO,"
                              +"new MulticastMaxPacketSize(1024),"
                              +"new MulticastTimeToLive(0),"
                              +"new UnicastSocketTimeout(120000)"
                          +"},"
                          +"null"
                      +")"
                  +")"

                +"}"
            +")";

        }//endif(proto-constraints)

        if(locators.length == 0) {
            locStrs = new String[1];
            locStrs[0] = 
                " "+OVERRIDE_COMPONENT_NAME+"."
               +"locatorsToJoin=new net.jini.core.discovery.LookupLocator[0]";
            if(setConstraints) {
                locStrs[0] = 
                        " "+OVERRIDE_COMPONENT_NAME+"."
                       +"locatorsToJoin=new net.jini.discovery."
                       +"ConstrainableLookupLocator[0]";
            }//endif
        } else {
            locStrs = new String[1+locators.length];
            String jUrl =  "jini://"+locators[0].getHost()+":"
                                                   +locators[0].getPort()+"/";
            locStrs[0] = 
                " "+OVERRIDE_COMPONENT_NAME+"."
               +"locatorsToJoin=new net.jini.core.discovery.LookupLocator[] {"
                  +"new net.jini.core.discovery.LookupLocator("
                                                               +"\""
                                                               +jUrl
                                                               +"\""
                                                               +")";
            if(setConstraints) {
                locStrs[0] = 
                         " "+OVERRIDE_COMPONENT_NAME+"."
                        +"locatorsToJoin=new net.jini.discovery."
                        +"ConstrainableLookupLocator[] {"
                        +"new net.jini.discovery.ConstrainableLookupLocator("
                                                               +"\""
                                                               +jUrl
                                                               +"\""
                                                               +","
                                                               +locConstraints
                                                               +")";
            }//endif
            for(int i=1; i<(locators.length-1); i++) {
                jUrl = "jini://"+locators[i].getHost()+":"
                                                   +locators[i].getPort()+"/";
                locStrs[i] = ",new net.jini.core.discovery.LookupLocator("
                                                               +"\""
                                                               +jUrl
                                                               +"\""
                                                               +")";
                if(setConstraints) {
                    locStrs[i] = 
                        ",new net.jini.discovery.ConstrainableLookupLocator("
                                                               +"\""
                                                               +jUrl
                                                               +"\""
                                                               +","
                                                               +locConstraints
                                                               +")";
                }//endif
            }//end loop
            locStrs[locators.length] = "}";
        }//endif
        return ConfigUtil.concat(locStrs);
    }//end overrideLocsStr

    /* ********************************************************************* */
    /* *************** Test Service started by the test above ************** */
    /* ********************************************************************* */
    static interface TestServiceInterface {
        public int getVal();
        public long getRenewDur();
        public void exitService()  throws RemoteException, ActivationException;
    }//end interface TestServiceInterface

    static interface RemoteTestServiceInterface extends Remote, 
                                                        ServiceProxyAccessor
    {
        public void exitService()  throws RemoteException, ActivationException;
    }//end interface RemoteTestServiceInterface

    static class RemoteTestServiceImpl implements ServerProxyTrust,
                                                  ProxyAccessor,
                                                  RemoteTestServiceInterface,
                                                  Starter
                                                  
    {
        private static final String COMPONENT_NAME = "test";
        private static final String JM_COMPONENT_NAME
                                             = "net.jini.lookup.JoinManager";
        final private int val;
        final private long renewDur;

        final private Configuration config;
        final private LoginContext loginContext;
        final private Uuid proxyID;
        final private ServiceID serviceID;
        final private ActivationID activationID;
        final private ActivationSystem activationSystem;
        volatile private boolean activationSystemUnregister;
        final private Exporter serverExporter;
        private TestServiceProxy outerProxy;
        private RemoteTestServiceInterface innerProxy;
        final private String[] groupsToJoin;
        final private LookupLocator[] locatorsToJoin;
        final private LookupDiscoveryManager ldm;
        private JoinManager joinMgr;
        private AccessControlContext context;
        private boolean started = false;

        RemoteTestServiceImpl(ActivationID activationID,
                              MarshalledObject data) throws Exception
        {// All exceptions are thrown prior to this Object being created.
            this(init((String[])data.get(), activationID, ActivationGroup.getSystem()));
        }//end constructor
        
        private RemoteTestServiceImpl(Init init){
            config = init.config;
            loginContext = init.loginContext;
            proxyID = init.proxyID;
            serviceID = init.serviceID;
            activationID = init.activationID;
            activationSystem = init.activationSystem;
            serverExporter = init.serverExporter;
            groupsToJoin = init.groupsToJoin;
            locatorsToJoin = init.locatorsToJoin;
            ldm = init.ldm;
            context = init.context;
            val = init.val.intValue();
            renewDur = init.renewDur.longValue();
        }
        
        public synchronized void start() throws Exception {
            if (started) return;
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){

                @Override
                public Object run() throws Exception {
                    innerProxy =
                           (RemoteTestServiceInterface)serverExporter.export(RemoteTestServiceImpl.this);
                    outerProxy = TestServiceProxy.createTestServiceProxy
                                                  (innerProxy, proxyID, val, renewDur);
                    joinMgr = new JoinManager(outerProxy, null, serviceID,
                                              ldm, null, config);
                    return null;
                }
                
            }, context);
            started = true;
            context = null; //Be careful not to store things on the stack.
        }

        public void exitService() throws RemoteException, ActivationException {
            if (activationSystemUnregister) {
	        activationSystem.unregisterGroup
                                          ( ActivationGroup.currentGroupID() );
                activationSystemUnregister = false;
            }//endif
            (new DestroyThread()).start();
        }//end exitService

        private static Init init(   String[] args, 
                                    ActivationID activationID, 
                                    ActivationSystem activationSystem
                                ) throws Exception 
        {
            Configuration config 
                    = ConfigurationProvider.getInstance
                           ( args,
                             RemoteTestServiceImpl.class.getClassLoader() );
            try {
                LoginContext loginContext = (LoginContext)Config.getNonNullEntry
                                                         (config,
                                                          COMPONENT_NAME,
                                                          "loginContext",
                                                          LoginContext.class);
                logger.log(Levels.HANDLED,
                           " ***** loginContext retrieved *****");
                return initWithLogin(config, loginContext, activationID, activationSystem);
            } catch (NoSuchEntryException e) {
                if(secureProto) {
                    logger.log(Levels.HANDLED, " ***** NO loginContext *****");
                }//endif
                return doInit(config, null, activationID, activationSystem);
            }
        }//end init

        private static Init initWithLogin(final Configuration config, 
                                   final LoginContext loginContext, 
                                   final ActivationID activationID, 
                                   final ActivationSystem activationSystem
                            ) throws LoginException, IOException, ConfigurationException 
        {
            loginContext.login();
            try {
                return Subject.doAsPrivileged
                  ( loginContext.getSubject(),
                    new PrivilegedExceptionAction<Init>() {
                        public Init run() throws Exception {
                            return doInit(  
                                        config, 
                                        loginContext, 
                                        activationID, 
                                        activationSystem
                                    );
                        }//end run
                    },
                    null );//end doAsPrivileged
            } catch (PrivilegedActionException e) {
                // Previous exception handling was broken, caught Throwable and
                // checked for instance of PrivilegedExceptionAction,
                // by mistake.
                Exception ex = e.getException();
                if(ex instanceof IOException)  throw (IOException)ex;
                if(ex instanceof ConfigurationException) 
                                          throw (ConfigurationException)ex;
                throw new RuntimeException(ex);
            }
        }//end initWithLogin
        
        private static Init doInit( Configuration config, 
                                    LoginContext loginContext, 
                                    ActivationID activationID, 
                                    ActivationSystem activationSystem
                                  ) throws ConfigurationException, IOException 
        {
            return new Init(config, loginContext, activationID, activationSystem);
        }//end doInit
        
        private static class Init {
            Integer val;
            Long renewDur;
            Uuid proxyID;
            ServiceID serviceID;
            LookupLocator [] locatorsToJoin;
            String[] groupsToJoin = DiscoveryGroupManagement.NO_GROUPS;
            LookupDiscoveryManager ldm;
            ActivationID activationID;
            ActivationSystem activationSystem;
            boolean activationSystemUnregister = false;
            Exporter serverExporter;
            LoginContext loginContext;
            AccessControlContext context;
            Configuration config;
            
            Init(Configuration config, 
                    LoginContext loginContext,
                    ActivationID activationID, 
                    ActivationSystem activationSystem) 
                    throws ConfigurationException, IOException
            {
                this.loginContext = loginContext;
                this.config = config;
                val = ((Integer)config.getEntry(COMPONENT_NAME,
                                                "val",
                                                int.class,
                                                Integer.valueOf(0)));
                renewDur = ((Long)config.getEntry
                                                (JM_COMPONENT_NAME,
                                                 "maxLeaseDuration",
                                                 long.class,
                                                 Long.valueOf(Lease.FOREVER)));
                proxyID = UuidFactory.generate();
                if(proxyID == null) throw new NullPointerException
                                                              ("proxyID == null");

                String serviceIDStr = (String)Config.getNonNullEntry
                                                                 (config,
                                                                  COMPONENT_NAME,
                                                                  "serviceID",
                                                                  String.class);
                serviceID = ConfigUtil.createServiceID(serviceIDStr);
                locatorsToJoin =
                         (LookupLocator[])config.getEntry(COMPONENT_NAME, 
                                                          "locatorsToJoin", 
                                                          LookupLocator[].class, 
                                                          new LookupLocator[0]);
                /* display the overridden config items */
                logger.log(Levels.HANDLED," TestService-"+val+": service ID = "
                                      +serviceID);
                for(int i=0; i<locatorsToJoin.length; i++) {
                    logger.log(Levels.HANDLED," TestService-"+val+": locsToJoin["
                                          +i+"] = "+locatorsToJoin[i]);
                }//end loop
                if(renewDur == Lease.FOREVER) {
                    logger.log(Levels.HANDLED,
                               " TestService-"+val+": lease duration = DEFAULT");
                } else {
                    logger.log(Levels.HANDLED,
                               " TestService-"+val+": lease duration = "
                               +renewDur);
                }//endif

                ldm = new LookupDiscoveryManager(groupsToJoin, locatorsToJoin,
                                                 null, config);

                ServerEndpoint endpoint = TcpServerEndpoint.getInstance(0);
                InvocationLayerFactory ilFactory = new BasicILFactory();
                Exporter defaultExporter = new BasicJeriExporter(endpoint,
                                                                 ilFactory,
                                                                 false,
                                                                 true);
                if(activationID != null) {
                    ProxyPreparer aidPreparer =
                      (ProxyPreparer)Config.getNonNullEntry
                                                       (config,
                                                        COMPONENT_NAME,
                                                        "activationIdPreparer",
                                                        ProxyPreparer.class,
                                                        new BasicProxyPreparer());
                    ProxyPreparer aSysPreparer = 
                      (ProxyPreparer)Config.getNonNullEntry
                                                       (config,
                                                        COMPONENT_NAME,
                                                        "activationSystemPreparer",
                                                        ProxyPreparer.class,
                                                        new BasicProxyPreparer());
                    this.activationID = (ActivationID)aidPreparer.prepareProxy
                                                                   (activationID);
                    this.activationSystem = (ActivationSystem)aSysPreparer.prepareProxy
                                                                (activationSystem);
                    defaultExporter = new ActivationExporter(activationID,
                                                             defaultExporter);
                    activationSystemUnregister = true;
                }//endif(activationID != null)
                try {
                    serverExporter = (Exporter)Config.getNonNullEntry
                                                                 (config,
                                                                  COMPONENT_NAME,
                                                                  "serverExporter",
                                                                  Exporter.class,
                                                                  defaultExporter);
                } catch(ConfigurationException e) {
                    throw new ExportException("Configuration exception while "
                                              +"retrieving service's exporter",
                                              e);
                }
                context = AccessController.getContext();
            }
            
        }

        public synchronized TrustVerifier getProxyVerifier() {
            return new ProxyVerifier(innerProxy, proxyID);
        }//end getProxyVerifier

        public synchronized Object getProxy() {
            return innerProxy;
        }//end getProxy

        public synchronized Object getServiceProxy() {
            return outerProxy;
        }//end getServiceProxy

        private static class DestroyThread extends Thread {
            public DestroyThread() {
                super("DestroyThread");
                setDaemon(false);
            }//end constructor
            public void run() {
                System.exit(0);
            }//end run
        }//end class DestroyThread

    }//end class RemoteTestServiceImpl

    static class TestServiceProxy implements TestServiceInterface,
                                             ReferentUuid, Serializable
    {
        private static final long serialVersionUID = 1L;
        final RemoteTestServiceInterface innerProxy;
        final Uuid proxyID;
        final int val;
        final long renewDur;

        public static TestServiceProxy createTestServiceProxy
                                       (RemoteTestServiceInterface innerProxy,
                                        Uuid proxyID,
                                        int  val,
                                        long renewDur)
        {
            if(innerProxy instanceof RemoteMethodControl) {
                return new ConstrainableTestServiceProxy
                                    (innerProxy, proxyID, val, renewDur, null);
            } else {
                return new TestServiceProxy(innerProxy,proxyID,val,renewDur);
            }//endif
        }//end createTestServiceProxy

        TestServiceProxy(RemoteTestServiceInterface innerProxy,
                         Uuid proxyID,
                         int val,
                         long renewDur)
        {
            this.innerProxy = innerProxy;
            this.proxyID = proxyID;
            this.val = val;
            this.renewDur = renewDur;
        }//end constructor

        public int getVal() {
            return val;
        }//end getVal

        public long getRenewDur() {
            return renewDur;
        }//end renewDur

        public void exitService() throws RemoteException, ActivationException {
            innerProxy.exitService();
        }//End exitService

        public Uuid getReferentUuid() {
            return proxyID;
        }//end getAdmin

        public int hashCode() {
	    return proxyID.hashCode();
        }//end hashCode

        public boolean equals(Object obj) {
	    return  ReferentUuids.compare(this,obj);
        }//end equals

        private void readObject(ObjectInputStream s)  
                                   throws IOException, ClassNotFoundException
        {
            s.defaultReadObject();
            if(innerProxy == null) {
                throw new InvalidObjectException("TestServiceProxy.readObject "
                                                 +"failure - innerProxy "
                                                 +"field is null");
            }//endif
            if(proxyID == null) {
                throw new InvalidObjectException("TestServiceProxy.readObject "
                                                 +"failure - proxyID "
                                                 +"field is null");
            }//endif
        }//end readObject

        private void readObjectNoData() throws InvalidObjectException {
            throw new InvalidObjectException
                                     ("no data found when attempting to "
                                     +"deserialize TestServiceProxy instance");
        }//end readObjectNoData

        static final class ConstrainableTestServiceProxy
                                                 extends    TestServiceProxy
                                                 implements RemoteMethodControl
        {
            static final long serialVersionUID = 1L;

            private static final Method[] methodMapArray = 
            {
                getMethod(TestServiceInterface.class,       "exitService",
                                                            new Class[] {} ),
                getMethod(RemoteTestServiceInterface.class, "exitService",
                                                            new Class[] {} ),
            };//end methodMapArray

            private MethodConstraints methodConstraints;

            private ConstrainableTestServiceProxy
                                      (RemoteTestServiceInterface innerProxy, 
                                       Uuid proxyID,
                                       int val,
                                       long renewDur,
                                       MethodConstraints methodConstraints)
            {
                super(constrainServer(innerProxy, methodConstraints),
                      proxyID, val, renewDur);
                this.methodConstraints = methodConstraints;
            }//end constructor

            private static RemoteTestServiceInterface constrainServer
                                      ( RemoteTestServiceInterface innerProxy,
                                        MethodConstraints constraints )
            {
                MethodConstraints newConstraints 
                 = ConstrainableProxyUtil.translateConstraints(constraints,
                                                               methodMapArray);
                RemoteMethodControl constrainedServer = 
              ((RemoteMethodControl)innerProxy).setConstraints(newConstraints);

                return ((RemoteTestServiceInterface)constrainedServer);
            }//end constrainServer

            public RemoteMethodControl setConstraints
                                              (MethodConstraints constraints)
            {
                return ( new ConstrainableTestServiceProxy
                           (innerProxy, proxyID, val, renewDur, constraints) );
            }//end setConstraints

            public MethodConstraints getConstraints() {
                return methodConstraints;
            }//end getConstraints

            private ProxyTrustIterator getProxyTrustIterator() {
                return new SingletonProxyTrustIterator(innerProxy);
            }//end getProxyTrustIterator

            private void readObject(ObjectInputStream s)  
                                   throws IOException, ClassNotFoundException
            {
                s.defaultReadObject();
                ConstrainableProxyUtil.verifyConsistentConstraints
                                                       (methodConstraints,
                                                        innerProxy,
                                                        methodMapArray);
            }//end readObject
        }//end class ConstrainableServiceProxy

        static Method getMethod(Class type,
                                String name,
			        Class[] parameterTypes)
        {
            try {
                return type.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                throw (Error)(new NoSuchMethodError
                                             (e.getMessage()).initCause(e));
            }
        }//end getMethod
    }//end class ServiceProxy

    final static class ProxyVerifier implements Serializable, TrustVerifier {
        private static final long serialVersionUID = 1L;
        private final RemoteMethodControl innerProxy;
        private final Uuid proxyID;
        ProxyVerifier(RemoteTestServiceInterface innerProxy, Uuid proxyID) {
            if( !(innerProxy instanceof RemoteMethodControl) ) {
                throw new UnsupportedOperationException
                         ("cannot construct verifier - canonical inner "
                          +"proxy is not an instance of RemoteMethodControl");
            } else if( !(innerProxy instanceof TrustEquivalence) ) {
                throw new UnsupportedOperationException
                             ("cannot construct verifier - canonical inner "
                              +"proxy is not an instance of TrustEquivalence");
            }//endif
            this.innerProxy = (RemoteMethodControl)innerProxy;
            this.proxyID = proxyID;
        }//end constructor

        public boolean isTrustedObject(Object obj,
                                       TrustVerifier.Context ctx)
                                                       throws RemoteException
        {
            if (obj == null || ctx == null) {
                throw new NullPointerException("arguments must not be null");
            }//endif
            RemoteMethodControl inputProxy;
            Uuid inputProxyID;
            if(obj instanceof TestServiceProxy.ConstrainableTestServiceProxy) {
                inputProxy =
                       (RemoteMethodControl)((TestServiceProxy)obj).innerProxy;
                inputProxyID = ((ReferentUuid)obj).getReferentUuid();
            } else if( obj instanceof RemoteMethodControl ) {
                inputProxy = (RemoteMethodControl)obj;
                inputProxyID = proxyID;
            } else {
                return false;
            }//endif
            final MethodConstraints mConstraints = inputProxy.getConstraints();
            final TrustEquivalence constrainedInnerProxy =
                     (TrustEquivalence)innerProxy.setConstraints(mConstraints);
            return (    constrainedInnerProxy.checkTrustEquivalence(inputProxy)
                     && proxyID.equals(inputProxyID) );
        }//end isTrustedObject
    }//end class ProxyVerifier

}//end class LeaseRenewDurRFE
