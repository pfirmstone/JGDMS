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

package org.apache.river.test.impl.joinmanager;

import org.apache.river.config.Config;
import org.apache.river.config.ConfigUtil;
import org.apache.river.logging.Levels;
import org.apache.river.proxy.ConstrainableProxyUtil;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import net.jini.export.ServiceProxyAccessor;
import org.apache.river.start.SharedActivatableServiceDescriptor;
import org.apache.river.start.SharedActivatableServiceDescriptor.Created;
import org.apache.river.start.SharedActivationGroupDescriptor;
import org.apache.river.start.SharedGroup;
import org.apache.river.test.spec.joinmanager.AbstractBaseTest;
import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import net.jini.activation.ActivationExporter;
import net.jini.activation.ActivationGroup;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lease.Lease;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.url.httpmd.HttpmdUtil;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.util.Startable;

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
    @Override
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        StringBuilder builder = new StringBuilder(500);
        System.setProperty("org.apache.river.qa.harness.runactivation", "true");
        getManager().startService("activationSystem");
        /* set shared convenience variables */
        host = sysConfig.getLocalHostName();
        jiniPort = sysConfig.getStringConfigVal("org.apache.river.jsk.port",
                                                "8080");
        qaPort = sysConfig.getStringConfigVal("org.apache.river.qa.port", "8081");

        qaHome = sysConfig.getStringConfigVal("org.apache.river.qa.home",
                                              "/vob/qa");
        builder.append(qaHome).append(sep).append("lib");
        qaHarnessLib = builder.toString();
        builder.delete(0, builder.length());
        qaTestHome = sysConfig.getStringConfigVal("org.apache.river.test.home",
                                                  "/vob/qa");
        builder.append(qaTestHome).append(sep).append("lib");
        qaTestLib = builder.toString();
        builder.delete(0, builder.length());
        builder.append(qaTestHome).append(sep).append("src").append(sep)
                .append("com").append(sep).append("sun").append(sep)
                .append("jini").append(sep).append("test");
        qaTest = builder.toString();
        builder.delete(0,builder.length());
        builder.append(qaTest).append(sep).append("spec");
        qaSpec = builder.toString();
        builder.delete(0,builder.length());
        builder.append(qaTest).append(sep).append("impl");
        qaImpl = builder.toString();
        builder.delete(0,builder.length());
        builder.append(qaImpl).append(sep).append("start").append(sep)
                .append("policy.all");
        policyAll = sysConfig.getStringConfigVal
                                    ("all.policyFile", builder.toString() );
        builder.delete(0, builder.length());
        logger.log(Levels.HANDLED,"policyFile = "+policyAll);

        jiniHome = sysConfig.getStringConfigVal("org.apache.river.jsk.home",
                                                "/vob/jive");
        builder.append(jiniHome).append(sep).append("lib");
        jiniLib   = builder.toString();
        builder.delete(0, builder.length());
        builder.append(jiniHome).append(sep).append("lib-dl");
        jiniLibDL = builder.toString();
        builder.delete(0, builder.length());

        proto = sysConfig.getStringConfigVal
                                  ("org.apache.river.qa.harness.configs", "jeri");
        secureProto = 
                (proto.compareToIgnoreCase("jeri") != 0) &&
                (proto.compareToIgnoreCase("jrmp") != 0) && 
                (proto.compareToIgnoreCase("http") != 0) && 
                (proto.compareToIgnoreCase("none") != 0);

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
        Lease srvcLease;
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
        logger.log(Levels.HANDLED, "noopServiceCodebase = {0}", noopServiceCodebase);
        String noopServicePolicyFile = policyAll;
        String noopServiceClasspath = sysConfig.getStringConfigVal
                                          ("sharedGroupImpl.classpath",null);
        logger.log(Levels.HANDLED, "noopServiceClasspath = {0}", noopServiceClasspath);
        String noopServiceImplName = sysConfig.getStringConfigVal
                                              ("sharedGroupImpl.impl",null);
        String[] noopServiceArgsArray =  new String[] { starterConfigFile };
        /* common service descriptor info shared by each TestService  */
        String serviceCodebase = serviceCodebase();
        logger.log(Levels.HANDLED, "serviceCodebase = {0}", serviceCodebase);
        String servicePolicyFile = policyAll;
        builder.append(qaHarnessLib).append(sep).append("jiniharness.jar")
                .append(pSep).append(jiniLib).append(sep)
                .append("jsk-platform.jar").append(pSep)
                .append(jiniLib).append(sep).append("jsk-lib.jar").append(pSep)
                .append(qaTestLib).append(sep).append("jinitests.jar");
        String serviceClasspath  = builder.toString();
        builder.delete(0, builder.length());
        logger.log(Levels.HANDLED, "serviceClasspath = {0}", serviceClasspath);
        String serviceImplName   = "org.apache.river.test.impl.joinmanager."
                                   +"LeaseRenewDurRFE$RemoteTestServiceImpl";
        /* If org.apache.river.qa.harness.configs (proto) is "none" then use only
         * the overrides; otherwise, use the standard test config file, and
         * override anything that file doesn't "know" about.
         */
        String serviceConfig = ( (proto.compareToIgnoreCase("none") == 0) ?
              "-" : sysConfig.getStringConfigVal("service.configFile","-") );
        logger.log(Levels.HANDLED, "serviceConfig = {0}", serviceConfig);

        /* service configuration overrides */
        String locConfigOverride = overrideLocsStr(locs);
        String[] serviceArgs0 = new String[nTestServices];
        String[] serviceArgs1 = new String[nTestServices];
        String[] serviceArgs2 = new String[nTestServices];
        String[] serviceArgs3 = new String[nTestServices];
        String servID = ".serviceID=";
        String val = ".val=";
        String invertedCommas = "\"";
        String maxLeaseDur = "net.jini.lookup.JoinManager.maxLeaseDuration=";
        for(int v=0; v<nTestServices; v++) {
            serviceArgs0[v] = locConfigOverride;//common override
            /* service-specific overrides - TestService-v */
            builder.append(OVERRIDE_COMPONENT_NAME).append(servID)
                    .append(invertedCommas).append(srvcID[v].toString()).append(invertedCommas);
            serviceArgs1[v] = builder.toString();
            builder.delete(0, builder.length());
            builder.append(OVERRIDE_COMPONENT_NAME).append(val).append(String.valueOf(v));
            serviceArgs2[v] = builder.toString();
            builder.delete(0, builder.length());
            builder.append(maxLeaseDur).append(String.valueOf(leaseDur[v]));
            serviceArgs3[v] = builder.toString();
            builder.delete(0, builder.length());
            /* log service overrides - TestService-v */
            logger.log(Levels.HANDLED,
                 "  *******************************************************");
            logger.log(Levels.HANDLED, "  ******** SETUP Config Overrides: TestService-{0} ********", v);
            logger.log(Levels.HANDLED, "  {0}", serviceArgs0[v]);
            logger.log(Levels.HANDLED, "  {0}", serviceArgs1[v]);
            logger.log(Levels.HANDLED, "  {0}", serviceArgs2[v]);
            logger.log(Levels.HANDLED, "  {0}", serviceArgs3[v]);
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
                int l = files.length;
                for(int i=0; i<l; i++) {
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
    @Override
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
                    logger.log(Levels.HANDLED,
                            "  TestService-{0}: lease duration = DEFAULT",
                            srvcProxy.getVal());
                } else {
                    logger.log(Levels.HANDLED, 
                            "  TestService-{0}: lease duration = {1}",
                            new Object[]{srvcProxy.getVal(), renewDur});
                }//endif
                logger.log(Levels.HANDLED, 
                        "  TestService-{0}: ***** ''KILL'' service {1} *****",
                        new Object[]{srvcProxy.getVal(), v});
                vmProxy[v].destroyVM(); /* "pull the plug" on the service */
                //srvcProxy.exitService(); /* make service crash on its own */

                /* verify lease expires when expected */
                if(sdm.lookup(tmpl[v],null) == null) {//no blocking this time
                    throw new TestException("TestService-"+v+": lease expired "
                                            +"before expected time ("
                                            +leaseDur[v]+")");
                }//endif
                /* Wait (dur+delta) for lease to expire */
                logger.log(Levels.HANDLED, 
                        "  TestService-{0}: wait at most ({1}+{2}) secs for lease to expire",
                        new Object[]{v, leaseDur[v]/1000, rfeDelta});
                /* First wait the lease duration */
                try{ 
                    Thread.sleep(leaseDur[v]);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                /* Wait a delta amount to account for communication latency */
                boolean leaseExpired = false;
                int i = 0;
                while( !leaseExpired && (i < rfeDelta) ) {
                    try{ Thread.sleep(1000); } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    i = i+1;
                    if(sdm.lookup(tmpl[v],null) == null) leaseExpired = true;
                }//end loop
                if(leaseExpired) {
                    logger.log(Levels.HANDLED, "  TestService-{0}: lease expired after ({1}+{2}) secs", new Object[]{v, leaseDur[v]/1000, i});
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
                    int l = files.length;
                    for(int i=0; i<l; i++) {
                        files[i].delete();
                    }//endif
                    sharedVMDirFD.delete();
                    logger.log(Levels.HANDLED, 
                            "  TestService-{0}: cleanup - removed shared VM dir ({1})",
                            new Object[]{v, sharedVMDir});
                }//endif
            }//end loop
        }
    }//end run

    private String[] sharedVMProperties(QAConfig sysConfig) throws Exception {
        ArrayList propsList = new ArrayList(43);
        /* miscellaneous items used in all configs */
        propsList.add("org.apache.river.qa.home");
        propsList.add(qaHome);

	propsList.add("org.apache.river.qa.harness.harnessJar");
	propsList.add(sysConfig.getStringConfigVal(
					    "org.apache.river.qa.harness.harnessJar", 
					    null));

	propsList.add("org.apache.river.qa.harness.testJar");
	propsList.add(sysConfig.getStringConfigVal(
					    "org.apache.river.qa.harness.testJar",
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
            logger.log(Levels.HANDLED,
                    "{0}={1}",
                    new Object[]{loginConfigKey, loginConfigVal});
            propsList.add(loginConfigKey);
            propsList.add(loginConfigVal);

            String secPropsKey = "java.security.properties";
            String secPropsVal = 
		sysConfig.getStringConfigVal("trust.policyProps", null);
	    if (secPropsVal == null) {
		throw new TestException("trust.policyProps is undefined");
	    }
            logger.log(Levels.HANDLED, 
                    "{0}={1}", 
                    new Object[]{secPropsKey, secPropsVal});
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
            logger.log(Levels.HANDLED, "{0}={1}",
                    new Object[]{trustStoreKey, trustStoreVal});
            propsList.add(trustStoreKey);
            propsList.add(trustStoreVal);

        } else if(proto.compareToIgnoreCase("kerberos") == 0) {

            String realmKey = "java.security.krb5.realm";
            String realmVal =
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.realm", null);
            logger.log(Levels.HANDLED,
                    "{0}={1}",
                    new Object[]{realmKey, realmVal});
            propsList.add(realmKey);
            propsList.add(realmVal);

            String kdcKey = "java.security.krb5.kdc";
            String kdcVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.kdc", null);
            logger.log(Levels.HANDLED, 
                    "{0}={1}", 
                    new Object[]{kdcKey, kdcVal});
            propsList.add(kdcKey);
            propsList.add(kdcVal);

            String keyTabKey = "keytab";
            String keyTabVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.aggregatePasswordFile", null);
            logger.log(Levels.HANDLED, 
                    "{0}={1}", 
                    new Object[]{keyTabKey, keyTabVal});
            propsList.add(keyTabKey);
            propsList.add(keyTabVal);

            String phoenixKey = "phoenix";
            String phoenixVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.phoenixPrincipal", null);
            logger.log(Levels.HANDLED, 
                    "{0}={1}",
                    new Object[]{phoenixKey, phoenixVal});
            propsList.add(phoenixKey);
            propsList.add(phoenixVal);

            String groupKey = "group";
            String groupVal = 	       
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.groupPrincipal", null);

            logger.log(Levels.HANDLED, "{0}={1}", new Object[]{groupKey, groupVal});
            propsList.add(groupKey);
            propsList.add(groupVal);

            String reggieKey = "reggie";
            String reggieVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.reggiePrincipal", null);
            logger.log(Levels.HANDLED, 
                    "{0}={1}", 
                    new Object[]{reggieKey, reggieVal});
            propsList.add(reggieKey);
            propsList.add(reggieVal);

            String mahaloKey = "mahalo";
            String mahaloVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.mahaloPrincipal", null);
            logger.log(Levels.HANDLED, 
                    "{0}={1}", 
                    new Object[]{mahaloKey, mahaloVal});
            propsList.add(mahaloKey);
            propsList.add(mahaloVal);

            String outriggerKey = "outrigger";
            String outriggerVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.outriggerPrincipal", null);
            logger.log(Levels.HANDLED, 
                    "{0}={1}", 
                    new Object[]{outriggerKey, outriggerVal});
            propsList.add(outriggerKey);
            propsList.add(outriggerVal);

            String mercuryKey = "mercury";
            String mercuryVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.mercuryPrincipal", null);
            logger.log(Levels.HANDLED, 
                    "{0}={1}", 
                    new Object[]{mercuryKey, mercuryVal});
            propsList.add(mercuryKey);
            propsList.add(mercuryVal);

            String normKey = "norm";
            String normVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.normPrincipal", null);
            logger.log(Levels.HANDLED, 
                    "{0}={1}",
                    new Object[]{normKey, normVal});
            propsList.add(normKey);
            propsList.add(normVal);

            String fiddlerKey = "fiddler";
            String fiddlerVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.fiddlerPrincipal", null);
            logger.log(Levels.HANDLED,
                    "{0}={1}", 
                    new Object[]{fiddlerKey, fiddlerVal});
            propsList.add(fiddlerKey);
            propsList.add(fiddlerVal);

            String testKey = "test";
            String testVal = 
		sysConfig.getStringConfigVal("org.apache.river.qa.harness.kerberos.testPrincipal", null);
            logger.log(Levels.HANDLED, "{0}={1}", 
                    new Object[]{testKey, testVal});
            propsList.add(testKey);
            propsList.add(testVal);

            String subjectCredsKey = "javax.security.auth.useSubjectCredsOnly";
            String subjectCredsVal = "false";
            logger.log(Levels.HANDLED, 
                    "{0}={1}",
                    new Object[]{subjectCredsKey, subjectCredsVal});
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
        StringBuilder builder = new StringBuilder(2400);
        String[] locStrs;

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
            builder.append("org.apache.river.config.KeyStores.getKeyStore")
                    .append("(")
                    .append("\"")
                    .append(truststoreURL)
                    .append("\"")
                    .append(",null")
                    .append(")");
            truststore = builder.toString(); 
            builder.delete(0, builder.length());
            logger.log(Levels.HANDLED, "  truststore = {0}", truststore);
        }//endif

        /* set protocol-specific locator constraints */
        if(    (proto.compareToIgnoreCase("jsse")  == 0)
            || (proto.compareToIgnoreCase("https") == 0) )
        {
            builder.append("org.apache.river.config.KeyStores.getX500Principal")
                    .append("(").append("\"").append("reggie").append("\"")
                    .append(",").append(truststore).append(")");
            principalReggie = builder.toString();
            builder.delete(0, builder.length());
            builder.append("org.apache.river.config.KeyStores.getX500Principal")
                    .append("(").append("\"").append("tester").append("\"")
                    .append(",").append(truststore).append(")");
            principalTester = builder.toString();
            builder.delete(0, builder.length());
            /* Note: the non-unicast constraints below are not necessary
             *       because all discovery is done using only unicast; but
             *       are included for reference.
             */
            locConstraints = builder
            .append("new BasicMethodConstraints").append("(")
                .append("new MethodDesc[]")
                .append("{ ")

                   .append("new MethodDesc")
                   .append("(")
                      .append("\"").append("multicastRequest").append("\"").append(",")
                      .append("new InvocationConstraints")
                      .append("(")
                         .append("new InvocationConstraint[]")
                         .append("{")
                              .append("ClientAuthentication.YES,")
                              .append("new ClientMinPrincipal(").append(principalTester).append("),")
                              .append("Integrity.YES,")
                              .append("DiscoveryProtocolVersion.TWO,")
                              .append("new MulticastMaxPacketSize(1024),")
                              .append("new MulticastTimeToLive(0),")
                              .append("new UnicastSocketTimeout(120000)")
                          .append("},")
                          .append("null")
                      .append(")")
                  .append(")")
                  .append(",")

                  .append("new MethodDesc")
                  .append("(")
                      .append("\"").append("multicastAnnouncement").append("\"").append(",")
                      .append("new InvocationConstraints")
                      .append("(")
                          .append("new InvocationConstraint[]")
                          .append("{")
                              .append("Integrity.YES,")
                              .append("ServerAuthentication.YES,")
                              .append("new ServerMinPrincipal(").append(principalReggie).append("),")
                              .append("DiscoveryProtocolVersion.TWO,")
                              .append("new MulticastMaxPacketSize(1024),")
                              .append("new MulticastTimeToLive(0),")
                              .append("new UnicastSocketTimeout(120000)")
                          .append("},")
                          .append("null")
                      .append(")")
                  .append(")")
                  .append(",")

                  .append("new MethodDesc")
                  .append("(")
                      .append("\"").append("unicastDiscovery").append("\"").append(",")
                      .append("new InvocationConstraints")
                      .append("(")
                          .append("new InvocationConstraint[]")
                          .append("{")
                              .append("Integrity.YES,")
                              .append("ServerAuthentication.YES,")
                              .append("new ServerMinPrincipal(").append(principalReggie).append("),")
                              .append("DiscoveryProtocolVersion.TWO,")
                              .append("new MulticastMaxPacketSize(1024),")
                              .append("new MulticastTimeToLive(0),")
                              .append("new UnicastSocketTimeout(120000)")
                          .append("},")
                          .append("null")
                      .append(")")
                  .append(")")
                 .append(",")

                  .append("new MethodDesc")
                  .append("(")
                      .append("\"").append("getRegistrar").append("\"").append(",")
                      .append("new InvocationConstraints")
                      .append("(")
                          .append("new InvocationConstraint[]")
                          .append("{")
                              .append("Integrity.YES,")
                              .append("ServerAuthentication.YES,")
                              .append("new ServerMinPrincipal(").append(principalReggie).append("),")
                              .append("DiscoveryProtocolVersion.TWO,")
                              .append("new MulticastMaxPacketSize(1024),")
                              .append("new MulticastTimeToLive(0),")
                              .append("new UnicastSocketTimeout(120000)")
                          .append("},")
                          .append("null")
                      .append(")")
                  .append(")")

                .append("}")
            .append(")").toString();
            builder.delete(0, builder.length());
        } else if(proto.compareToIgnoreCase("kerberos") == 0) {
	    String pName = config.getStringConfigVal("org.apache.river.qa.harness.kerberos.reggiePrincipal", null);
            principalReggie = builder
                  .append("new javax.security.auth.kerberos.KerberosPrincipal")
                   .append("(")
                       .append("\"").append(pName).append("\"")
                   .append(")").toString();
            builder.delete(0, builder.length());
	    pName = config.getStringConfigVal("org.apache.river.qa.harness.kerberos.testPrincipal", null);
            principalTester = builder
                  .append("new javax.security.auth.kerberos.KerberosPrincipal")
                   .append("(")
                       .append("\"").append(pName).append("\"")
                   .append(")").toString();
            builder.delete(0, builder.length());
            locConstraints = builder
            .append("new BasicMethodConstraints")
            .append("(")
                .append("new MethodDesc[]")
                .append("{ ")

                  .append("new MethodDesc")
                  .append("(")
                      .append("\"").append("unicastDiscovery").append("\"").append(",")
                      .append("new InvocationConstraints")
                      .append("(")
                          .append("new InvocationConstraint[]")
                          .append("{")
                              .append("Integrity.YES,")
                              .append("ServerAuthentication.YES,")
                              .append("new ServerMinPrincipal(").append(principalReggie).append("),")
                              .append("DiscoveryProtocolVersion.TWO,")
                              .append("new MulticastMaxPacketSize(1024),")
                              .append("new MulticastTimeToLive(0),")
                              .append("new UnicastSocketTimeout(120000)")
                          .append("},")
                          .append("null")
                      .append(")")
                  .append(")")
                  .append(",")

                  .append("new MethodDesc")
                  .append("(")
                      .append("\"").append("getRegistrar").append("\"").append(",")
                      .append("new InvocationConstraints")
                      .append("(")
                          .append("new InvocationConstraint[]")
                          .append("{")
                              .append("Integrity.YES,")
                              .append("ServerAuthentication.YES,")
                              .append("new ServerMinPrincipal(").append(principalReggie).append("),")
                              .append("DiscoveryProtocolVersion.TWO,")
                              .append("new MulticastMaxPacketSize(1024),")
                              .append("new MulticastTimeToLive(0),")
                              .append("new UnicastSocketTimeout(120000)")
                          .append("},")
                          .append("null")
                      .append(")")
                  .append(")")
                  .append(",")

                  .append("new MethodDesc")
                  .append("(")
                      .append("new InvocationConstraints")
                      .append("(")
                          .append("new InvocationConstraint[]")
                          .append("{")
                              .append("DiscoveryProtocolVersion.TWO,")
                              .append("new MulticastMaxPacketSize(1024),")
                              .append("new MulticastTimeToLive(0),")
                              .append("new UnicastSocketTimeout(120000)")
                          .append("},")
                          .append("null")
                      .append(")")
                  .append(")")

                .append("}")
            .append(")").toString();
            builder.delete(0, builder.length());
        }//endif(proto-constraints)

        if(locators.length == 0) {
            locStrs = new String[1];
            locStrs[0] = builder
                .append(" ").append(OVERRIDE_COMPONENT_NAME).append(".")
                .append("locatorsToJoin=new net.jini.core.discovery.LookupLocator[0]")
                .toString();
            builder.delete(0, builder.length());
            if(setConstraints) {
                locStrs[0] = builder
                       .append(" ").append(OVERRIDE_COMPONENT_NAME).append(".")
                       .append("locatorsToJoin=new net.jini.discovery.")
                       .append("ConstrainableLookupLocator[0]").toString();
                builder.delete(0, builder.length());
            }//endif
        } else {
            locStrs = new String[1+locators.length];
            String jUrl =  builder
                    .append("jini://").append(locators[0].getHost()).append(":")
                                      .append(locators[0].getPort()).append("/")
                    .toString();
            builder.delete(0, builder.length());
            locStrs[0] = builder
               .append(" ").append(OVERRIDE_COMPONENT_NAME).append(".")
               .append("locatorsToJoin=new net.jini.core.discovery.LookupLocator[] {")
                  .append("new net.jini.core.discovery.LookupLocator(")
                                                               .append("\"")
                                                               .append(jUrl)
                                                               .append("\"")
                                                               .append(")")
                    .toString();
            builder.delete(0, builder.length());
            if(setConstraints) {
                locStrs[0] = builder
                        .append(" ").append(OVERRIDE_COMPONENT_NAME).append(".")
                        .append("locatorsToJoin=new net.jini.discovery.")
                        .append("ConstrainableLookupLocator[] {")
                        .append("new net.jini.discovery.ConstrainableLookupLocator(")
                                                               .append("\"")
                                                               .append(jUrl)
                                                               .append("\"")
                                                               .append(",")
                                                               .append(locConstraints)
                                                               .append(")")
                        .toString();
                builder.delete(0, builder.length());
            }//endif
            for(int i=1; i<(locators.length-1); i++) {
                jUrl = builder
                       .append("jini://").append(locators[i].getHost()).append(":")
                                       .append(locators[i].getPort()).append("/")
                        .toString();
                builder.delete(0, builder.length());
                locStrs[i] = builder
                        .append(",new net.jini.core.discovery.LookupLocator(")
                                                               .append("\"")
                                                               .append(jUrl)
                                                               .append("\"")
                                                               .append(")")
                        .toString();
                builder.delete(0, builder.length());
                if(setConstraints) {
                    locStrs[i] = builder
                        .append(",new net.jini.discovery.ConstrainableLookupLocator(")
                                                               .append("\"")
                                                               .append(jUrl)
                                                               .append("\"")
                                                               .append(",")
                                                               .append(locConstraints)
                                                               .append(")")
                            .toString();
                    builder.delete(0, builder.length());
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

    public static interface RemoteTestServiceInterface extends Remote, 
                                                        ServiceProxyAccessor
    {
        public void exitService()  throws RemoteException, ActivationException;
    }//end interface RemoteTestServiceInterface

    static class RemoteTestServiceImpl implements ServerProxyTrust,
                                                  ProxyAccessor,
                                                  RemoteTestServiceInterface,
                                                  Startable
                                                  
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

        @Override
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
                        @Override
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
                logger.log(Levels.HANDLED, 
                        " TestService-{0}: service ID = {1}", 
                        new Object[]{val, serviceID});
                for(int i=0; i<locatorsToJoin.length; i++) {
                    logger.log(Levels.HANDLED,
                            " TestService-{0}: locsToJoin[{1}] = {2}",
                            new Object[]{val, i, locatorsToJoin[i]});
                }//end loop
                if(renewDur == Lease.FOREVER) {
                    logger.log(Levels.HANDLED,
                            " TestService-{0}: lease duration = DEFAULT",
                            val);
                } else {
                    logger.log(Levels.HANDLED,
                            " TestService-{0}: lease duration = {1}",
                            new Object[]{val, renewDur});
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

        @Override
        public synchronized TrustVerifier getProxyVerifier() {
            return new ProxyVerifier(innerProxy, proxyID);
        }//end getProxyVerifier

        @Override
        public synchronized Object getProxy() {
            return innerProxy;
        }//end getProxy

        @Override
        public synchronized Object getServiceProxy() {
            return outerProxy;
        }//end getServiceProxy

        private static class DestroyThread extends Thread {
            public DestroyThread() {
                super("DestroyThread");
                setDaemon(false);
            }//end constructor
            @Override
            public void run() {
                System.exit(0);
            }//end run
        }//end class DestroyThread

    }//end class RemoteTestServiceImpl

    @AtomicSerial
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

	TestServiceProxy(GetArg arg) throws IOException {
	    this(notNull(arg.get("innerProxy", null, RemoteTestServiceInterface.class),
	       "TestServiceProxy.readObject failure - innerProxy field is null" ),
		notNull(arg.get("proxyID", null, Uuid.class),
		"TestServiceProxy.readObject failure - proxyID field is null"),
		arg.get("val", 0),
		arg.get("renewDur", 0L));
	}
	
	private static <T> T notNull(T arg, String message) throws IOException {
	    if (arg == null) throw new InvalidObjectException(message);
	    return arg;
	}

        @Override
        public int getVal() {
            return val;
        }//end getVal

        @Override
        public long getRenewDur() {
            return renewDur;
        }//end renewDur

        @Override
        public void exitService() throws RemoteException, ActivationException {
            innerProxy.exitService();
        }//End exitService

        @Override
        public Uuid getReferentUuid() {
            return proxyID;
        }//end getAdmin

        @Override
        public int hashCode() {
	    return proxyID.hashCode();
        }//end hashCode

        @Override
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

	@AtomicSerial
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

            private final MethodConstraints methodConstraints;

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

	    ConstrainableTestServiceProxy(GetArg arg) throws IOException {
		super(check(arg));
		methodConstraints = (MethodConstraints) arg.get("methodConstraints", null);
	    }
	    
	    private static GetArg check(GetArg arg) throws IOException {
		TestServiceProxy tsp = new TestServiceProxy(arg);
		MethodConstraints methodConstraints =  
		    arg.get("methodConstraints", null, MethodConstraints.class);
		ConstrainableProxyUtil.verifyConsistentConstraints
                                                       (methodConstraints,
                                                        tsp.innerProxy,
                                                        methodMapArray);
		return arg;
	    }

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

            @Override
            public RemoteMethodControl setConstraints
                                              (MethodConstraints constraints)
            {
                return ( new ConstrainableTestServiceProxy
                           (innerProxy, proxyID, val, renewDur, constraints) );
            }//end setConstraints

            @Override
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

    @AtomicSerial
    final static class ProxyVerifier implements Serializable, TrustVerifier {
        private static final long serialVersionUID = 1L;
        private final RemoteMethodControl innerProxy;
        private final Uuid proxyID;
        ProxyVerifier(RemoteTestServiceInterface innerProxy, Uuid proxyID) {
	    this(check(innerProxy, RemoteMethodControl.class, 
		    "cannot construct verifier - canonical inner "
		    + "proxy is not an instance of RemoteMethodControl"
		    ),
		check(proxyID, TrustEquivalence.class, 
		    "cannot construct verifier - canonical inner "
		      +"proxy is not an instance of TrustEquivalence"
		    ),
		true
	    );
        }//end constructor
	
	private ProxyVerifier(RemoteTestServiceInterface innerProxy,
		Uuid proxyID, boolean check) {
            this.innerProxy = (RemoteMethodControl)innerProxy;
            this.proxyID = proxyID;
	}

	ProxyVerifier(GetArg arg) throws IOException {
	    this(arg.get("innerProxy", null, RemoteTestServiceInterface.class),
		    arg.get("proxyID", null, Uuid.class));
	} 
	
	private static <T> T check(T arg, Class<?> c, String message) {
	    if (!c.isInstance(arg)) throw new UnsupportedOperationException(message); 
	    return arg;
	}

        @Override
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
