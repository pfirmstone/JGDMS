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
package com.sun.jini.test.share;

import com.sun.jini.qa.harness.AdminManager;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.services.lookupsimulator.LookupSimulatorProxyInterface;
import com.sun.jini.test.share.BaseQATest.LocatorGroupsPair;
import com.sun.jini.test.share.BaseQATest.LookupListener;
import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest.LDSEventListener;
import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest.RegistrationInfo;
import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;
import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest.DiscoveryStruct;
import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest.RegGroupsPair;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryRegistration;
import net.jini.lookup.DiscoveryAdmin;

/**
 * This class was refactored from BaseQATest, there are some minor issues
 * remaining:
 * 
 * TODO: Ensure lookup service instances can only be started once.
 * TODO: If the index is incorrect report the new index back to the caller.
 * 
 * @author peter
 */
public class LookupServices {
    /** the logger */
    private static final Logger logger = 
    Logger.getLogger("com.sun.jini.qa.harness");
    /* instance variables not modified after construction */
    private final QAConfig config;
    private final AdminManager manager;
    private final String implClassname;
    private final int testType;
    private final boolean debugsync;
    
    private final int originalAnnounceInterval;
    private final int announceInterval;
    private final int minNAnnouncements;
    private final int nIntervalsToWait;

    private final int nLookupServices;
    private final int nAddLookupServices;
    private final int nRemoteLookupServices;
    private final int nAddRemoteLookupServices;
    private final int nSecsLookupDiscovery;
    private final int nSecsServiceDiscovery;
    private final int nSecsJoin;
    
    private final int nServices;//local/serializable test services
    private volatile int nAddServices;//additional local/serializable services


    /* Attributes per service */
    private final int nAttributes;
    private final int nAddAttributes;

    private final String remoteHost;

    /* Data structures - lookup services */
    private final Object lock = new Object(); // synchronizes state of all List<LocatorGroupsPair> after construction.
    private final List<LocatorGroupsPair> initLookupsToStart = new ArrayList<LocatorGroupsPair>(11);
    private final List<LocatorGroupsPair> addLookupsToStart  = new ArrayList<LocatorGroupsPair>(11);
    private final List<LocatorGroupsPair> allLookupsToStart  = new ArrayList<LocatorGroupsPair>(11);
    private final List<LocatorGroupsPair> lookupsStarted    = new ArrayList<LocatorGroupsPair>(11);
    // end lock scope.

    private final List<ServiceRegistrar> lookupList = new ArrayList<ServiceRegistrar>(1);//synchronize on lookupList
    private final ConcurrentMap<Object, String[]> genMap = new ConcurrentHashMap<Object, String[]>(11);
    private final ConcurrentMap<Object,RemoteEventListener> registrationMap = new ConcurrentHashMap<Object,RemoteEventListener>(11);;
    /* Need to keep track of member groups by the index of the corresponding
     * lookup service so those groups can be mapped to the correct member
     * groups configuration item. 
     */
    private final List<String[]> memberGroupsList = new ArrayList<String[]>(11);//built during construction, no mutation after.
    
    /* Need to keep a local mapping of registrars to their corresponding 
     * locators and groups so that when a registrar is discarded (indicating
     * that a remote call to retrieve the discarded registrar's locator and/or
     * group information should not be made), the locator and/or groups can
     * be retrieved through a non-remote mechanism. Each time a lookup service
     * is started, the registrar and its locator/groups pair are added to this
     * map.
     */
    private final ConcurrentMap<ServiceRegistrar,LocatorGroupsPair> regsToLocGroupsMap = 
            new ConcurrentHashMap<ServiceRegistrar,LocatorGroupsPair>(11);
    
    /* relocate to LookupServices from discovery services AbstractBaseTest */
    private final List<DiscoveryStruct> useGroupAndLocDiscovery0 = new CopyOnWriteArrayList<DiscoveryStruct>();
    private final List<DiscoveryStruct> useGroupAndLocDiscovery1 = new CopyOnWriteArrayList<DiscoveryStruct>();
    private final List<DiscoveryStruct> useOnlyGroupDiscovery    = new CopyOnWriteArrayList<DiscoveryStruct>();
    private final List<DiscoveryStruct> useOnlyLocDiscovery      = new CopyOnWriteArrayList<DiscoveryStruct>();

    public LookupServices(QAConfig config, AdminManager manager, int fastTimeout) throws TestException{
        this.config = config;
        this.manager = manager;
        debugsync = config.getBooleanConfigVal("qautil.debug.sync",false);
        testType = config.getIntConfigVal("com.sun.jini.testType",
                                       BaseQATest.AUTOMATIC_LOCAL_TEST);
        /* begin harness info */
        logger.log(Level.FINE, " ----- Harness Info ----- ");
        String harnessCodebase = System.getProperty("java.rmi.server.codebase",
                                                    "no codebase");
        logger.log(Level.FINE, " harness codebase         -- {0}", harnessCodebase);

        String harnessClasspath = System.getProperty("java.class.path",
                                                    "no classpath");
        logger.log(Level.FINE, " harness classpath        -- {0}", harnessClasspath);

        String discDebug = System.getProperty("net.jini.discovery.debug",
                                              "false");
        logger.log(Level.FINE, " net.jini.discovery.debug        -- {0}", discDebug);
        String regDebug = System.getProperty("com.sun.jini.reggie.proxy.debug",
                                             "false");
        logger.log(Level.FINE, " com.sun.jini.reggie.proxy.debug -- {0}", regDebug);
        String joinDebug = System.getProperty("com.sun.jini.join.debug",
                                              "false");
        logger.log(Level.FINE, " com.sun.jini.join.debug         -- {0}", joinDebug);
        String sdmDebug = System.getProperty("com.sun.jini.sdm.debug","false");
        logger.log(Level.FINE, " com.sun.jini.sdm.debug          -- {0}", sdmDebug);

        /* end harness info */

        /* begin lookup info */
        logger.log(Level.FINE, " ----- Lookup Service Info ----- ");
        implClassname = config.getStringConfigVal
                                 ("net.jini.core.lookup.ServiceRegistrar.impl",
                                  "no implClassname");
        int nLuServ = 0;
        nLuServ = config.getIntConfigVal
                           ("net.jini.lookup.nLookupServices",
                             nLuServ);
        int nRemLuServ = 0;
        nRemLuServ = config.getIntConfigVal
                           ("net.jini.lookup.nRemoteLookupServices",
                             nRemLuServ);
        int nAddLuServ = 0;
        nAddLuServ = config.getIntConfigVal
                           ("net.jini.lookup.nAddLookupServices",
                             nAddLuServ);
        int nAddRemLuServ = 0;
        nAddRemLuServ = config.getIntConfigVal
                           ("net.jini.lookup.nAddRemoteLookupServices",
                             nAddRemLuServ);
        if(testType == BaseQATest.MANUAL_TEST_REMOTE_COMPONENT) {
            nLuServ = nRemLuServ;
            nAddLuServ = nAddRemLuServ;
            nRemLuServ = 0;
            nAddRemLuServ = 0;
        }//endif
        this.nLookupServices = nLuServ;
        this.nRemoteLookupServices = nRemLuServ;
        this.nAddRemoteLookupServices = nAddRemLuServ;
        this.nAddLookupServices = nAddLuServ;
        nSecsLookupDiscovery = config.getIntConfigVal("net.jini.lookup.nSecsLookupDiscovery", 30);
        logger.log(Level.FINE, " # of lookup services to start            -- {0}", nLuServ);
        logger.log(Level.FINE, " # of additional lookup services to start -- {0}", nAddLuServ);
        logger.log(Level.FINE, " seconds to wait for discovery            -- {0}", nSecsLookupDiscovery);
        /* Multicast announcement info - give priority to the command line */
        int annInterval = 2 * 60 * 1000;
        int originalAnnounceInterval = 0;
        try {
            int sysInterval = Integer.getInteger
                                 ("net.jini.discovery.announce",0).intValue();
            originalAnnounceInterval = sysInterval;
            if(sysInterval > 0) {
                annInterval = sysInterval;
            } else {
                sysInterval = config.getIntConfigVal
                                           ("net.jini.discovery.announce",0);
                if(sysInterval > 0) annInterval = sysInterval;
            }
            Properties props = System.getProperties();
            props.put("net.jini.discovery.announce",
                       (new Integer(annInterval)).toString());
            System.setProperties(props);
        } catch (SecurityException e) {
            // Ignore
        } finally {
            this.announceInterval = annInterval;
            this.originalAnnounceInterval = originalAnnounceInterval;
        }
        logger.log(Level.FINE, " discard if no announcements in (nSecs =) -- {0}", (annInterval/1000));
        minNAnnouncements = config.getIntConfigVal
                         ("net.jini.discovery.minNAnnouncements", 2);
        nIntervalsToWait = config.getIntConfigVal
                          ("net.jini.discovery.nIntervalsToWait", 3);
        /* end lookup info */

        fastTimeout = 
            config.getIntConfigVal("com.sun.jini.test.share.fastTimeout", 
                                 fastTimeout);

        /* begin local/serializable service info */
        nServices = config.getIntConfigVal("net.jini.lookup.nServices",0);
        nAddServices = config.getIntConfigVal("net.jini.lookup.nAddServices",0);
        int nAttr = 0;
        int nAddAttr = 0;
        int nSecJoin = 30;
        int nSecServiceDiscovery = 30;
        if( (nServices+nAddServices) > 0) {
            nAttr = config.getIntConfigVal("net.jini.lookup.nAttributes",nAttr);
            nAddAttr = config.getIntConfigVal("net.jini.lookup.nAddAttributes", nAddAttr);
            nSecJoin = config.getIntConfigVal("net.jini.lookup.nSecsJoin", nSecJoin);
            nSecServiceDiscovery = config.getIntConfigVal("net.jini.lookup.nSecsServiceDiscovery", nSecServiceDiscovery);
            logger.log(Level.FINE, " ----- General Service Info ----- ");
            logger.log(Level.FINE, " # of initial basic services to register  -- {0}", nServices);
            logger.log(Level.FINE, " # of additional basic srvcs to register  -- {0}", nAddServices);
            logger.log(Level.FINE, " # of attributes per service              -- {0}", nAttr);
            logger.log(Level.FINE, " # of additional attributes per service   -- {0}", nAddAttr);
            logger.log(Level.FINE, " # of seconds to wait for service join    -- {0}", nSecJoin);
            logger.log(Level.FINE, " # of secs to wait for service discovery  -- {0}", nSecServiceDiscovery);
        }//endif(nServices+nAddServices > 0)
        this.nAttributes = nAttr;
        this.nAddAttributes = nAddAttr;
        this.nSecsJoin = nSecJoin;
        this.nSecsServiceDiscovery = nSecServiceDiscovery;
        /* Handle remote/local components of manual tests */
        remoteHost = config.getStringConfigVal("net.jini.lookup.remotehost",
                                            "UNKNOWN_HOST");
        switch(testType) {
            case BaseQATest.MANUAL_TEST_REMOTE_COMPONENT:
                logger.log(Level.FINE, " ***** REMOTE COMPONENT OF A MANUAL TEST "
                        +"(remote host = {0}) ***** ", remoteHost);
                break;
            case BaseQATest.MANUAL_TEST_LOCAL_COMPONENT:
                logger.log(Level.FINE, " ***** LOCAL COMPONENT OF A MANUAL TEST "
                        +"(remote host = {0}) ***** ", remoteHost);
                logger.log(Level.FINE, " ----- Remote Lookup Service Info ----- ");
                logger.log(Level.FINE, " # of remote lookup services              -- {0}", nRemoteLookupServices);
                logger.log(Level.FINE, " # of additional remote lookup services   -- {0}", nAddRemoteLookupServices);
                logger.log(Level.FINE, " # of remote basic services               -- {0}", nServices);
                break;
        }//end switch(testType)
        
        /** Retrieves and stores the information needed to configure any lookup
         *  services that will be started for the current test run.
         */

        /* Retrieve the member groups & locator of each lookup */
        /* For all cases except MANUAL_TEST_LOCAL_COMPONENT, the number of
         * remote lookups is zero (see getSetupInfo). For that case, we must
         * handle the remote lookup services BEFORE handling the local lookups.
         * This is because the local component may be both starting local
         * lookups, and discovering remote lookups; but the remote component
         * will only be starting the remote lookups, and will therefore have
         * no knowledge of the local lookups. Because the groups and ports are
         * ordered by the index numbers in the config file, if the local info
         * were to be handled first, then the local component of the test
         * would fall out of alignment with the remote component.
         */
        int n0 = 0;
        int n1 = n0 + nRemLuServ;
        for(int i=0;i<n1;i++) {//initial remote lookups
            /* Member groups for remote lookup service i */
            String groupsArg = config.getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            /* Do NOT use unique groups names since clocks on the local
             * and remote sides are not synchronized, and host names are
             * different
             */
            String[] memberGroups = config.parseString(groupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) {
                logger.log(Level.FINER, "memberGroups = ALL_GROUPS");
                continue;
            }
            memberGroupsList.add(memberGroups);
            /* Locator for initial remote lookup service i */
            initLookupsToStart.add
                          (getLocatorGroupsPair(i,memberGroups));
        }//end loop
        /* Remote lookups started after initial remote lookups */
        n0 = n1;
        n1 = n0 + nAddRemLuServ;
        for(int i=n0;i<n1;i++) {//additional remote lookups
            /* Member groups for remote lookup service i */
            String groupsArg = config.getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            /* Use NON-unique groups for remote lookups */
            String[] memberGroups = config.parseString(groupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) continue;
            memberGroupsList.add(memberGroups);
            /* Locator for additional remote lookup service i */
            addLookupsToStart.add
                          (getLocatorGroupsPair(i,memberGroups));
        }//end loop
        /* Handle all lookups to be started locally */
        n0 = n1;
        n1 = n0 + nLuServ;
        int portBias = n0;
        for(int i=n0;i<n1;i++) {//initial local lookups
            /* Member groups for lookup service i */
            String groupsArg = config.getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            if(testType == BaseQATest.AUTOMATIC_LOCAL_TEST) {
                /* Use unique group names to avoid conflict with other tests */
                groupsArg = config.makeGroupsUnique(groupsArg);
            }//endif
            String[] memberGroups = config.parseString(groupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) {
                logger.log(Level.FINER, "memberGroups = All_Groups");
                continue;
            }
            memberGroupsList.add(memberGroups);
            /* Locator for initial lookup service i */
            initLookupsToStart.add
                          (getLocatorGroupsPair(i-portBias,memberGroups));
        }//end loop
        /* The lookup services to start after the initial lookup services */
        n0 = n1;
        n1 = n0 + nAddLuServ;
        for(int i=n0;i<n1;i++) {//additional local lookups
            /* Member groups for lookup service i */
            String groupsArg = config.getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            if(testType == BaseQATest.AUTOMATIC_LOCAL_TEST) {
                /* Use unique group names to avoid conflict with other tests */
                groupsArg = config.makeGroupsUnique(groupsArg);
            }//endif
            String[] memberGroups = config.parseString(groupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) continue;
            memberGroupsList.add(memberGroups);
            /* Locator for additional lookup service i */
            addLookupsToStart.add
                          (getLocatorGroupsPair(i-portBias,memberGroups));
        }//end loop
        /* Populate the ArrayList allLookupsToStart */
        for(int i=0;i<initLookupsToStart.size();i++) {
            allLookupsToStart.add(initLookupsToStart.get(i));
        }//end loop
        for(int i=0;i<addLookupsToStart.size();i++) {
            allLookupsToStart.add(addLookupsToStart.get(i));
        }//end loop

    }

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup services INITIALLY
     *  needed by that test run. Useful when an initial set of lookups are
     *  to be started during construct processing, and (possibly) an additional
     *  set of lookups are to be started at some later time, after the test
     *  has already begun execution.
     * @throws Exception 
     */
    public void startInitLookups() throws Exception {
        if(nLookupServices > 0) {
            /* Skip over remote lookups to the indices of the local lookups */
            int n0 = nRemoteLookupServices + nAddRemoteLookupServices;
            int n1 = n0 + nLookupServices;
            synchronized (lock){
                for(int i=n0;i<n1;i++) {
                    LocatorGroupsPair pair = initLookupsToStart.get(i);
                    int port = (pair.getLocator()).getPort();
                    if(portInUse(port)) port = 0;
                    String hostname = startLookup(i,port, pair.getLocator().getHost());
                    logger.log(Level.FINEST, "service host is ''{0}'', this host is ''{1}''", new Object[]{hostname, config.getLocalHostName()});
                    if(port == 0) {
                        LocatorGroupsPair locGroupsPair = lookupsStarted.get(i);
                        initLookupsToStart.set(i,locGroupsPair);
                        allLookupsToStart.set(i,locGroupsPair);
                    }
                    LocatorGroupsPair p = initLookupsToStart.get(i);
                    LookupLocator l = p.getLocator();
                    logger.log(Level.FINEST, "init locator {0} = {1}", new Object[]{i, l});
                }//end loop
                if(testType != BaseQATest.MANUAL_TEST_LOCAL_COMPONENT) {
                    if(!BaseQATest.listsEqual(initLookupsToStart,lookupsStarted)) {
                        logger.log(Level.FINE,
                                          " initial lookups started != "
                                          +"initial lookups wanted");
                        logger.log(Level.FINE,
                                          " initial lookups started --");
                        displayLookupStartInfo(lookupsStarted);
                        logger.log(Level.FINE,
                                          " initial lookups wanted --");
                        displayLookupStartInfo(initLookupsToStart);
    //                    tearDown(); //Have caller perform tearDown.
                        throw new TestException("initial lookups started != "
                                                  +"initial lookups wanted");
                    }//endif
                }//endif
            }// end synchronized lock.
        }//endif(nLookupServices > 0)
    }//end startInitLookups

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, any additional lookup services 
     *  needed by that test run. Useful when an initial set of lookups are
     *  to be started during construct processing, and an additional set of
     *  lookups are to be started at some later time, after the test
     *  has already begun execution.
     * @throws Exception 
     * @throws TestException
     */
    public void startAddLookups() throws Exception {
        if(nAddLookupServices > 0) {
            /* Skip over remote lookups and lookups already started to the
             * indices of the additional local lookups
             */
            synchronized (lock){
                int n0 = nRemoteLookupServices + nAddRemoteLookupServices
                                               + lookupsStarted.size();
                int n1 = n0 + nAddLookupServices;
                for(int i=n0;i<n1;i++) {
                    int j = i-n0;
                    LocatorGroupsPair pair = addLookupsToStart.get(j);
                    int port = (pair.getLocator()).getPort();
                    if(portInUse(port)) port = 0;
                    startLookup(i,port, pair.getLocator().getHost());
                    if(port == 0) {
                        LocatorGroupsPair locGroupsPair = lookupsStarted.get(i);
                        addLookupsToStart.set(j,locGroupsPair);
                        allLookupsToStart.set(i,locGroupsPair);
                    }
                    LocatorGroupsPair p = addLookupsToStart.get(j);
                    LookupLocator l = p.getLocator();
                    logger.log(Level.FINEST, "add locator {0} = {1}", new Object[]{j, l});
                }//end loop
                if(testType != BaseQATest.MANUAL_TEST_LOCAL_COMPONENT) {
                    if(!BaseQATest.listsEqual(allLookupsToStart,lookupsStarted)) {
                        logger.log(Level.FINE,
                                          " additional lookups started != "
                                          +"additional lookups wanted");
                        logger.log(Level.FINE,
                                          " additional lookups started --");
                        displayLookupStartInfo(lookupsStarted);
                        logger.log(Level.FINE,
                                          " additional lookups wanted --");
                        displayLookupStartInfo(allLookupsToStart);
    //                    tearDown(); let caller perform tear down.
                        throw new TestException("additional lookups started != "
                                                  +"additional lookups wanted");
                    }//endif
                }//endif
            }// end synchronized lock.
        }//endif(nAddLookupServices > 0)
    }//end startAddLookups

    /**
     * Start next lookup.  If no configured lookups exists a new one is
     * created dynamically and appended to the end of the lookup lists.
     * 
     * @return index of lookup started.
     * @throws Exception  
     */
    public int startNextLookup(String info) throws Exception {
        synchronized (lock){
            int indx = curLookupListSize(info);
            LocatorGroupsPair pair = allLookupsToStart.get(indx);
            int port = 0;
            if (pair != null){
                port = (pair.getLocator()).getPort();
                if(portInUse(port)) port = 0;//use randomly chosen port
                startLookup(indx, port, pair.getLocator().getHost());
            } else {
                String host = config.getLocalHostName();
                startLookup(indx, port, host);
            }
            if (port == 0) refreshLookupLocatorListsAt(indx);
            return indx;
        }
    }
    
    /** 
     * Start a lookup service with configuration referenced by the
     * given parameter values.
     *
     * @param indx the index of lookup services within the set of
     *             lookups to start for this test
     * @param port the port the lookup service is to use
     * @param serviceHost the host name the lookup service is to use.
     * @return the name of the system the lookup service was started on
     * @throws Exception if something goes wrong
     */
    private String startLookup(int indx, int port, String serviceHost) throws Exception {
        logger.log(Level.FINE, " starting lookup service {0}", indx);
        /* retrieve the member groups with which to configure the lookup */
        String[] memberGroups = memberGroupsList.get(indx);
        ServiceRegistrar lookupProxy = null;
	String simulatorName = 
	    "com.sun.jini.test.services.lookupsimulator.LookupSimulatorImpl";
        if(implClassname.equals(simulatorName)) {
            DiscoveryProtocolSimulator generator = null;
            if(debugsync) logger.log(Level.FINE,
                              "     BaseQATest.startLookup - "
                              +"sync on lookupList --> requested");
            synchronized(lookupList) {
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"sync on lookupList --> granted");
                /* Use either a random or an explicit locator port */
                generator = new DiscoveryProtocolSimulator
                                               (config,memberGroups, port, (LookupSimulatorProxyInterface) manager.startLookupService());
                generator.start();
                genMap.put( generator, memberGroups );
                lookupProxy = generator.getLookupProxy();
                lookupList.add( lookupProxy );
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"  added new proxy to lookupList");
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"sync on lookupList --> released");
            }//end sync(lookupList)
            /* Force non-unique groups for manual tests */
            if(    (testType == BaseQATest.MANUAL_TEST_REMOTE_COMPONENT)
                || (testType == BaseQATest.MANUAL_TEST_LOCAL_COMPONENT) ) 
            {
                generator.setMemberGroups(memberGroups);
            }//endif
        } else {//start a non-simulated lookup service implementation
            if(debugsync) logger.log(Level.FINE,
                              "     BaseQATest.startLookup - "
                              +"sync on lookupList --> requested");
            synchronized(lookupList) {
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"sync on lookupList --> granted");
		/* returned proxy is already prepared */
                lookupProxy = manager.startLookupService(serviceHost);
                lookupList.add( lookupProxy );
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"  added new proxy to lookupList");
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"sync on lookupList --> released");
            }//end sync(lookupList)
            genMap.put( lookupProxy, memberGroups );
            /* Force non-unique groups for manual tests */
            if(    (testType == BaseQATest.MANUAL_TEST_REMOTE_COMPONENT)
                || (testType == BaseQATest.MANUAL_TEST_LOCAL_COMPONENT) ) 
            {
                if(lookupProxy instanceof Administrable) {
                    Object admin = ((Administrable)lookupProxy).getAdmin();
		    admin = config.prepare("test.reggieAdminPreparer", 
						admin);
                    if(admin instanceof DiscoveryAdmin) {
                        ((DiscoveryAdmin)admin).setMemberGroups(memberGroups);
                    }
                }
            }
        }

        LookupLocator lookupLocator = 
	    QAConfig.getConstrainedLocator(lookupProxy.getLocator());
        LocatorGroupsPair locGroupsPair = new LocatorGroupsPair(lookupLocator,
                                                               memberGroups);
        synchronized (lock){
            try {
                lookupsStarted.add(indx,locGroupsPair);
            } catch(IndexOutOfBoundsException e) {
                /* There must be remote lookups, simply add it without the index */
                lookupsStarted.add(locGroupsPair);
            }
        }
        regsToLocGroupsMap.put(lookupProxy,locGroupsPair);

        LocatorsUtil.displayLocator(lookupLocator,
                                    "  locator",Level.FINE);
        String displayGroups = GroupsUtil.toCommaSeparatedStr(memberGroups);
        if(displayGroups.equals("")) displayGroups = "NO_GROUPS";
        logger.log(Level.FINE, "   memberGroup(s) = {0}", displayGroups);
        /* Because the lookup discovery service can perform both group and
         * locator discovery, some tests will focus only on group 
         * discovery, some only on locator discovery, and some on both
         * group and locator discovery. To provide these tests with
         * a variety of options for the groups and/or locators to discover,
         * a number of data structures are populated below which the
         * tests can use to configure the lookup discovery service for
         * discovery. In some cases, only the member groups from above
         * are stored, in other cases only the locators are stored, and
         * in still other cases, a mix of groups and locators are stored.
         * The case where the groups and locators are mixed is intended
         * to provide tests that verify the lookup discovery service's 
         * ability to handle both group and locator discovery with a
         * set that can be used to configure the lookup discovery service
         * to discover some registrars through only group discovery,
         * some registrars through only locator discovery, and some
         * registrars through both group and locator discovery.
         */
        if( (indx%3) == 0 ) {// add both groups and locs
            useGroupAndLocDiscovery0.add
            (new DiscoveryStruct(memberGroups,lookupLocator,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
            useGroupAndLocDiscovery1.add
            (new DiscoveryStruct(memberGroups,(LookupLocator) null,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
        } else if( (indx%3) == 1 ) {// add only the groups
            useGroupAndLocDiscovery0.add
            (new DiscoveryStruct(memberGroups,(LookupLocator) null,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
            useGroupAndLocDiscovery1.add(new DiscoveryStruct
                                (memberGroups,null,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
        } else if( (indx%3) == 2 ) {// add only the locators
            useGroupAndLocDiscovery0.add
            (new DiscoveryStruct((String[]) null,lookupLocator,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
            useGroupAndLocDiscovery1.add(new DiscoveryStruct
                                (memberGroups,lookupLocator,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
        }//endif
        useOnlyGroupDiscovery.add
            (new DiscoveryStruct(memberGroups,(LookupLocator) null,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
        useOnlyLocDiscovery.add
            (new DiscoveryStruct((String[]) null,lookupLocator,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
	return serviceHost;
    }
    
    /** Method that compares the given port to the ports of all the lookup
     *  services that have been currently started. Returns <code>true</code>
     *  if the given port equals any of the ports referenced in the set
     *  lookup services that have been started; <code>false</code>
     *  otherwise. This method is useful for guaranteeing unique port
     *  numbers when starting lookup services.
     * @param port
     * @return true if port in use. 
     */
    private boolean portInUse(int port) {
        if (port == 0) return false; // Ephemeral
        for(int i=0;i<lookupsStarted.size();i++) {
            LocatorGroupsPair pair = lookupsStarted.get(i);
            int curPort = (pair.getLocator()).getPort();
            if(port == curPort) return true;
        }//end loop
        // Open a client ephemeral socket and attempt to connect to
        // port on localhost to see if someone's listening.
        Socket sock = null;
        try {
            sock = new Socket();
            if (sock instanceof SocketOptions){
                // Socket terminates with a RST rather than a FIN, so there's no TIME_WAIT
                try {
                    ((SocketOptions) sock).setOption(SocketOptions.SO_LINGER, Integer.valueOf(0));
                } catch (SocketException se) {
                    // Ignore, not supported.
                    logger.log( Level.FINEST, "SocketOptions set SO_LINGER threw an Exception", se);
                }
            }
            SocketAddress add = new InetSocketAddress(port);
            sock.connect(add, 3000); // Try to connect for up to three seconds 
            // We were able to connect to a socket listening on localhost
            return true;
        } catch (SocketTimeoutException e){
            // There might be a stale process assume in use.
            logger.log( Level.FINEST, "Socket timed out while trying to connect", e);
            return true;
        } catch (IOException e){
            // There was nothing listening on the socket so it's probably free.
            // or it timed out.
            return false;
        } finally {
            if (sock != null){
                try {
                    sock.close();
                } catch (IOException ex){
                    logger.log( Level.FINEST, "Socket threw exception while attempting to close", ex);
                }// Ignore
            }
        }
    }//end portInUse
    
    private void refreshLookupLocatorListsAt(int index){
        //Only update existing records, ignore new dynamicly started lookups.
        LocatorGroupsPair locGroupsPair = lookupsStarted.get(index);
        /* index range of init lookups */
        int initLookupsBegin = nRemoteLookupServices + nAddRemoteLookupServices;
        int initLookupsEnd = initLookupsBegin + nLookupServices;
        /* index range of add lookups */
        int addLookupsBegin = initLookupsEnd;
        int addLookupsEnd = addLookupsBegin + nAddLookupServices;
        /* update lookup lists */
        if (index >= initLookupsBegin && index < initLookupsEnd) initLookupsToStart.set(index,locGroupsPair);
        if (index >= addLookupsBegin && index < addLookupsEnd) addLookupsToStart.set(index-addLookupsBegin, locGroupsPair);
        if (index < allLookupsToStart.size()) allLookupsToStart.set(index,locGroupsPair);
    }
    
    private LocatorGroupsPair getLocatorGroupsPair(int indx, String[] groups) throws TestException {
        LookupLocator l = getTestLocator(indx);
        return new BaseQATest.LocatorGroupsPair(l, groups);
    }
    
    /** Constructs a <code>LookupLocator</code> using configuration information
     *  corresponding to the value of the given parameter <code>indx</code>.
     *  Useful when lookup services need to be started, or simply when
     *  instances of <code>LookupLocator</code> need to be constructed with
     *  meaningful state.
     * @param indx
     * @return LookupLocator
     * @throws TestException  
     */
    public LookupLocator getTestLocator(int indx) throws TestException {
        /* Locator for lookup service corresponding to indx */
        int port = config.getServiceIntProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "port", indx);
        if (port == Integer.MIN_VALUE) {
	    port = 4160;
	}
	String hostname = 
	    config.getServiceHost("net.jini.core.lookup.ServiceRegistrar", indx, null);
	logger.log(Level.FINER, "getServiceHost returned {0}", hostname);
	if (hostname == null) {
	    hostname = "localhost";
	    try {
		hostname = InetAddress.getLocalHost().getHostName();
	    } catch(UnknownHostException e) {
		e.printStackTrace();
	    }
	}
        return QAConfig.getConstrainedLocator(hostname,port);
    }//end getTestLocator
    
    /** Constructs a <code>LookupLocator</code> using configuration information
     *  corresponding to the value of the given parameter <code>indx</code>.
     *  Useful when lookup services need to be started, or simply when
     *  instances of <code>LookupLocator</code> need to be constructed with
     *  meaningful state.
     */
    protected LookupLocator getRemoteTestLocator(int indx) {
        /* Locator for lookup service corresponding to indx */
        int port = config.getServiceIntProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "port", indx);
        if (port == Integer.MIN_VALUE) {
	    port = 4160;
	}
	// used for book keeping only, so don't need a constrainable locator
        return QAConfig.getConstrainedLocator(remoteHost,port);
    }//end getRemoteTestLocator

    /** Method used for debugging. Displays the following information about
     *  the contents of the given list of <code>LocatorGroupsPair</code>
     *  instances: the number of elements, the locator of the associated
     *  lookup service, and the member groups of the associated lookup service.
     */
    private void displayLookupStartInfo(List<LocatorGroupsPair> lookupList) {
        logger.log(Level.FINE, "   # of lookups = {0}", lookupList.size());
        for(int i=0;i<lookupList.size();i++) {
            LocatorGroupsPair pair = lookupList.get(i);
            LookupLocator loc    = pair.getLocator();
            String[]      groups = pair.getGroups();
            logger.log(Level.FINE, "     locator lookup[{0}] = {1}", new Object[]{i, loc});
            GroupsUtil.displayGroupSet(groups,"       group", Level.FINE);
        }//end loop
    }//end displayLookupStartInfo

    
    /** Since the lookup discovery utility typically sends a unicast
     *  announcement at startup, resulting in immediate discovery by
     *  unicast, the lookup service which generates the multicast 
     *  announcements won't send its first announcement until after
     *  net.jini.discovery.announce number of milliseconds. This means
     *  that until that first multicast announcement arrives, the lookup
     *  discovery utility will have no point of reference (no initial time
     *  stamp) with which to determine if the announcements have indeed
     *  stopped. Thus, multicast monitoring doesn't really begin until after
     *  the first announcement arrives. It is for this reason that before
     *  the multicast announcements are stopped, it may be of value to wait
     *  enough time so as to guarantee that at least one announcement has
     *  been sent. This method can be used by tests that wish to provide
     *  such a guarantee.
     *
     *  The time this method waits is computed from the configurable number
     *  of announcement time intervals over which to wait (nIntervalsToWait),
     *  and the configurable minimum number of announcements over which
     *  unreachability is determined (minNAnnouncements).
     * 
     *  Note that this method should only be used when the test uses
     *  simulated lookup services.
     */
    protected void verifyAnnouncementsSent() {
        logger.log(Level.FINE, " number of announcements to wait for    -- {0}", minNAnnouncements);
        logger.log(Level.FINE, " number of intervals to wait through    -- {0}", nIntervalsToWait);
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            DiscoveryProtocolSimulator curGen = 
                                  (DiscoveryProtocolSimulator)iter.next();
            logger.log(Level.FINE, " gen {0} - waiting ... announcements so far -- {1}", new Object[]{i, curGen.getNAnnouncementsSent()});
            for(int j=0; ((j<nIntervalsToWait)
                &&(curGen.getNAnnouncementsSent()< minNAnnouncements));j++)
            {
                DiscoveryServiceUtil.delayMS(announceInterval);
                logger.log(Level.FINE, " gen {0} - waiting ... announcements so far -- {1}", new Object[]{i, curGen.getNAnnouncementsSent()});
            }//end loop
            logger.log(Level.FINE, " gen {0} - wait complete ... announcements  -- {1}", new Object[]{i, curGen.getNAnnouncementsSent()});
        }//end loop
    }//end verifyAnnouncementsSent
    
    
    /** This method replaces, with the given set of groups, the current
     *  member groups of the given lookup service (<code>generator</code>).
     *  This method returns an instance of <code>LocatorGroupsPair</code> in
     *  which the locator of the given lookup service is paired with the given
     *  set of new groups.
     */
    private LocatorGroupsPair replaceMemberGroups(Object generator,
                                                    String[] newGroups)
                                                        throws RemoteException
    {
        ServiceRegistrar regProxy = null;
        if(generator instanceof DiscoveryProtocolSimulator) {
            regProxy
                    = ((DiscoveryProtocolSimulator)generator).getLookupProxy();
            ((DiscoveryProtocolSimulator)generator).setMemberGroups(newGroups);
        } else {
            regProxy = (ServiceRegistrar)generator;
            DiscoveryAdmin admin
                    = (DiscoveryAdmin)( ((Administrable)regProxy).getAdmin() );
	    try {
                admin = (DiscoveryAdmin)
		        config.prepare("test.reggieAdminPreparer", admin);
	    } catch (TestException e) {
		throw new RemoteException("Problem preparing admin", e);
	    }
            admin.setMemberGroups(newGroups);
        }//endif
        LookupLocator loc = QAConfig.getConstrainedLocator(regProxy.getLocator());
        return new LocatorGroupsPair(loc,newGroups);
    }//end replaceMemberGroups

    /** Depending on the value of the boolean parameter <code>alternate</code>,
     *  this method either replaces the current member groups of the
     *  given lookup service (<code>generator</code>) with a new set
     *  of groups containing NONE of the current groups of interest
     *  (<code>alternate</code> == <code>false</code>), or a new set
     *  of groups in which, alternately, half the elements are equal
     *  to their counterparts in the original set, and half are different
     *  from their counterparts.
     *  
     *  This method is intended to guarantee that the new set of groups 
     *  that replaces the member groups of the given lookup service contains
     *  some, if not all, new groups different from the original groups.
     *
     *  This method returns an instance of  <code>LocatorGroupsPair</code>
     *  in which the locator of the given lookup service is paired with the
     *  set of new groups generated by this method.
     * @param generator
     * @param alternate 
     * @return
     * @throws RemoteException  
     */
    private LocatorGroupsPair replaceMemberGroups(Object generator,
                                                    boolean alternate)
                                                        throws RemoteException
    {
        ServiceRegistrar regProxy = null;
	DiscoveryAdmin admin = null;
	// only prepare the real proxy (until simulators are secure)
        if(generator instanceof DiscoveryProtocolSimulator) {
            regProxy
                   = ((DiscoveryProtocolSimulator)generator).getLookupProxy();
	    admin = (DiscoveryAdmin)( ((Administrable)regProxy).getAdmin() );
        } else {
            regProxy = (ServiceRegistrar)generator;
	    admin = (DiscoveryAdmin)( ((Administrable)regProxy).getAdmin() );
	    try {
                admin = (DiscoveryAdmin)
		        config.prepare("test.reggieAdminPreparer", admin);
	    } catch (TestException e) {
		throw new RemoteException("Problem preparing admin", e);
	    }
        }//endif
        String[] groups    = admin.getMemberGroups();
        String[] newGroups =  ( (groups.length > 0) ? 
                          (new String[groups.length]) :
                          (new String[] {"Group_"+regProxy.getServiceID()}) );
        if(newGroups.length == 0) {
            logger.log(Level.FINE, "   NO_GROUPS");
        } else {
            for(int i=0;i<newGroups.length;i++) {
                boolean oddIndx = !((i%2) == 0);
                // This looks dubious, the new String may be replaced with the
                // original instance by the jvm.
                newGroups[i] = ( (alternate && oddIndx) ? new String(groups[i])
                                             : new String(groups[i]+"_new") );
                logger.log(Level.FINE, "   newGroups["+i+"] = "
                                           +newGroups[i]);
            }//end loop
        }//endif
        return replaceMemberGroups(generator,newGroups);
    }//end replaceMemberGroups

    /** Depending on the value of the boolean parameter <code>alternate</code>,
     *  for each lookup service that has been started, this method either
     *  replaces the current member groups of the lookup service with a
     *  new set of groups containing NONE of the current groups of interest
     *  (<code>alternate</code> == <code>false</code>), or a new set of
     *  groups in which, alternately, half the elements are equal to their
     *  counterparts in the original set, and half are different from their
     *  counterparts.
     *
     *  This method is intended to guarantee that the new set of groups 
     *  that replaces the member groups of each lookup service that was
     *  started contains some, if not all, new groups different from the
     *  original groups.
     *  
     *  This method returns an <code>ArrayList</code> in which each element
     *  is an instance of <code>LocatorGroupsPair</code> corresponding to one
     *  of the lookup services that was started; and in which the locator of
     *  the associated lookup service is paired with the set of new groups
     *  generated by this method.
     *
     *  This method can be used to cause various discovered/discarded/changed
     *  events to be sent by the discovery helper utility.
     * @param alternate
     * @return List containing LocatorGroupsPair
     */
   public List<LocatorGroupsPair> replaceMemberGroups(boolean alternate) {
        List<LocatorGroupsPair> locGroupsList = new LinkedList<LocatorGroupsPair>();
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            /* Replace the member groups of the current lookup service */
            logger.log(Level.FINE, " lookup service {0}"+" - "
                              +"replacing member groups with -- ", i);
            try {
                locGroupsList.add(replaceMemberGroups(iter.next(),alternate));
            } catch(RemoteException e) {
                logger.log(Level.FINE, 
                                  " failed to change member groups "+"for lookup service {0}", i);
                e.printStackTrace();
            }
        }//end loop
        return locGroupsList;
    }//end replaceMemberGroups


    /** For each lookup service that has been started, this method replaces
     *  the lookup service's current member groups with the given set of
     *  groups.
     *
     *  This method returns an <code>ArrayList</code> in which each element
     *  is an instance of <code>LocatorGroupsPair</code> corresponding to one
     *  of the lookup services that was started; and in which the locator of
     *  the associated lookup service is paired with given set of groups.
    * @param newGroups
    * @return  
    */
   public List<LocatorGroupsPair> replaceMemberGroups(String[] newGroups) {
        return replaceMemberGroups(genMap.size(),newGroups);
    }//end replaceMemberGroups

    /** For N of the lookup services started, this method replaces the lookup
     *  service's current member groups with the given set of groups; where
     *  N is determined by the value of the given <code>nReplacements</code>
     *  parameter.
     *
     *  This method returns an <code>List</code> in which each element
     *  is an instance of <code>LocatorGroupsPair</code> corresponding to one
     *  of the lookup services that was started; and in which the locator of
     *  the associated lookup service is paired with the given set of groups.
    * @param nReplacements
    * @param newGroups 
    * @return List containing LocatorGroupsPair
    */
   public List<LocatorGroupsPair> replaceMemberGroups(int nReplacements,
                                           String[] newGroups)
   {
        List<LocatorGroupsPair> locGroupsList = new LinkedList<LocatorGroupsPair>();
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            Object generator = iter.next();
            if(i<nReplacements) {
                /* Replace the member groups of the current lookup service */
                logger.log(Level.FINE, " lookup service {0}"+" - "
                                  +"replacing member groups with --", i);
                if(newGroups.length == 0) {
                    logger.log(Level.FINE, "   NO_GROUPS");
                } else {
                    for(int j=0;j<newGroups.length;j++) {
                        logger.log(Level.FINE, "   newGroups[{0}] = {1}", new Object[]{j, newGroups[j]});
                    }//end loop
                }//endif
                try {
                    locGroupsList.add
                                  ( replaceMemberGroups(generator,newGroups) );
                } catch(RemoteException e) {
                    logger.log(Level.FINE, 
                                      " failed to change member groups "+"for lookup service {0}", i);
                    e.printStackTrace();
                }
            } else {//(i >= nReplacements)
                /* Leave member groups of the current lookup service as is*/
                logger.log(Level.FINE, " lookup service {0}"+" - "
                                  +"leaving member groups unchanged --", i);
                ServiceRegistrar regProxy = null;
                if(generator instanceof DiscoveryProtocolSimulator) {
                    regProxy
                    = ((DiscoveryProtocolSimulator)generator).getLookupProxy();
                } else {
                    regProxy = (ServiceRegistrar)generator;
                }//endif
                try {
                    LookupLocator loc = QAConfig.getConstrainedLocator(regProxy.getLocator());
                    String[] groups   = regProxy.getGroups();
                    if(groups.length == 0) {
                        logger.log(Level.FINE, "   NO_GROUPS");
                    } else {
                        for(int j=0;j<groups.length;j++) {
                            logger.log(Level.FINE, "   groups[{0}] = {1}", new Object[]{j, groups[j]});
                        }//end loop
                    }//endif
                    locGroupsList.add
                                  ( new LocatorGroupsPair(loc,groups) );
                } catch(RemoteException e) {
                    logger.log(Level.FINE, 
                                      " failed on locator/groups retrieval "+"for lookup service {0}", i);
                    e.printStackTrace();
                }
            }//endif
        }//end loop
        return locGroupsList;
    }//end replaceMemberGroups

   
    /** Common code, used by discovery service test classes, 
     *  invoked by the run() method. This will method will replace the
     *  member groups of each lookup service started during construct. How
     *  those groups are replaced is dependent on the values of the input
     *  parameters. 
     * 
     *  Each group component of the replaceMap parameter will be replaced
     *  with the new groups whenever the lookpup service proxy whose
     *  groups are being replaced is also a key of the replaceMap.
     *
     *  If the replaceGroups parameter is non-null, that set of groups
     *  will be used to replace the groups of each lookup service; otherwise
     *  the set used to replace the goups will be constructed based on the
     *  the current group names.
     *
     *  If the alternate parameter is true, then when replacing the
     *  groups of any lookup service, only every other element of that
     *  set will be replaced with a new group name.
     *
     *  The discardType parameter is used to determine which map to populate
     *  with the expected discard data that is used to verify the assertion.
     */
    public void replaceGroups(Object curGen,
                                 int genIndx,
                                 String[] replaceGroups,
                                 boolean alternate,
                                 int discardType)
    {
        try {
            ServiceRegistrar lookupProxy = null;
            if( curGen instanceof DiscoveryProtocolSimulator ) {
                DiscoveryProtocolSimulator generator 
                                         = (DiscoveryProtocolSimulator)curGen;
                lookupProxy = generator.getLookupProxy();
            } else {
                lookupProxy = (ServiceRegistrar)curGen;
            }//endif
            String[] curGroups = lookupProxy.getGroups();
            String[] newGroups =  new String[] {"Group_"
                                                +lookupProxy.getServiceID()};
            if(replaceGroups != null) {
                newGroups = replaceGroups;
            } else {
                if((curGroups != null) && (curGroups.length > 0)) {
                    if(alternate) {
                        int len = curGroups.length;
                        newGroups = new String[len];
                        for(int j=0;j<=(len/2);j++) {
                            int k = 2*j;
                            if(k >= len) break;
                            newGroups[k] = new String(curGroups[k]+"_new");
                            if( (k+1) >= len ) break;
                            newGroups[k+1]= new String(curGroups[k+1]);
                        }//end loop
                    } else { //don't alternate
                        newGroups = new String[] {curGroups[0]+"_new"};
                    }//endif (alternate)
                }//endif (curGroups!=null)
            }//endif
            if( (newGroups != null) && (newGroups.length <= 0) ) {
                logger.log(Level.FINE, "   newGroups = NO_GROUPS");
            } else {
                GroupsUtil.displayGroupSet(newGroups,
                                           "   newGroups",Level.FINE);
            }//endif
	    Iterator iter = registrationMap.values().iterator();
            for(int j=0;iter.hasNext();j++) {
                LDSEventListener listener = (LDSEventListener)iter.next();
                RegistrationInfo regInfo  = listener.getRegInfo();
                if(regInfo.getExpectedDiscoveredMap().containsKey(lookupProxy)) {
                    regInfo.getExpectedDiscoveredMap().put(lookupProxy,newGroups);
                }//endif
                Map expectedDiscardedMap = getExpectedDiscardedMap
                                                        (regInfo,discardType);
                if(expectedDiscardedMap.containsKey(lookupProxy)) {
                    if(discardType != AbstractBaseTest.PASSIVE_DISCARDED) {
                        expectedDiscardedMap.put(lookupProxy,newGroups);
                    } else {//(discardType == PASSIVE_DISCARDED)
                        /* If announcements were stopped, but the groups are
                         * replaced to generate an event, then the LDM will
                         * send discarded events for those lookups that are
                         * being discovered by only group discovery by ALL
                         * the registrations; and the LDS will then send
                         * discarded events that reflect the OLD groups, not
                         * the NEW groups being set here. On the other hand,
                         * for a given lookup service whose announcements have
                         * been stopped, if at least one registration is 
                         * interested in discovering that lookup by locator
                         * as well as by group, then the LDM will send a
                         * changed event, but the LDS will send a discarded
                         * event reflecting the NEW groups to each registration
                         * that is not also interested in the lookup by
                         * locator. The LDS does because the new groups are
                         * not of interest to those registrations. Thus, test
                         * for this situation and change the expected discarded
                         * groups for a registration only if that registration
                         * also wishes to discover the lookup by its locator.
                         */
                        try {
                            if( discoverByLocAnyReg(lookupProxy) ) {
                                expectedDiscardedMap.put(lookupProxy,
                                                         newGroups);
                            }//endif
                        } catch(Exception e1) { /*skip this lookupProxy*/ }
                    }//endif
                }//endif
            }//end loop(iter)
            setLookupMemberGroups(lookupProxy,newGroups);
        } catch(RemoteException e) {
            logger.log(Level.FINE, "failed to replace member groups "
                              +"for lookup service "+genIndx);
            e.printStackTrace();
        } catch(TestException e) {
            logger.log(Level.FINE, "failed to replace member groups "
                              +"for lookup service "+genIndx);
            e.printStackTrace();
        }
    }//end replaceGroups

    public void replaceGroups(String[] replaceGroups,
                                 boolean alternate,
                                 int discardType)
    {
        if(!genMap.isEmpty()) {
            logger.log(Level.FINE, "replacing member groups for each lookup service ...");
	    Iterator iter = genMap.keySet().iterator();

            for(int i=0;iter.hasNext();i++) {
                replaceGroups(iter.next(),i,replaceGroups,
                              alternate,discardType);
            }//end loop
        }//endif
    }//end replaceGroups

    protected void replaceWithNoGroups(int discardType) {
        replaceGroups(DiscoveryGroupManagement.NO_GROUPS, false, discardType);
    }//end replaceWithNoGroups

    protected void changeGroups(int discardType) {
        replaceGroups(null,true, discardType); // alternates new groups
    }//end changeGroups

    protected void replaceGroups(int discardType) {
        replaceGroups(null,false, discardType); // basic replacement
    }//end replaceGroups

    protected Map getExpectedDiscardedMap(RegistrationInfo regInfo, 
                                          int              discardType)
    {
        switch(discardType) {
            case AbstractBaseTest.ACTIVE_DISCARDED:
                return regInfo.getExpectedActiveDiscardedMap();
            case AbstractBaseTest.COMM_DISCARDED:
                return regInfo.getExpectedCommDiscardedMap();
            case AbstractBaseTest.NO_INTEREST_DISCARDED:
                return regInfo.getExpectedNoInterestDiscardedMap();
            case AbstractBaseTest.PASSIVE_DISCARDED:
                return regInfo.getExpectedPassiveDiscardedMap();
        }//end switch
        return regInfo.getExpectedActiveDiscardedMap();
    }//end getExpectedDiscardedMap
    
    /** Convenience method that determines whether the locator of the
     *  given registrar is a locator-of-interest to any of the current 
     *  client registrations. If at least one such registration wishes
     *  to discover the given registration by locator, then this method
     *  returns true; otherwise, it returns false.
     *
     *  @throws jave.rmi.RemoteException upon failing to retrieve the locator
     *          of the given registrar.
     */
    protected boolean discoverByLocAnyReg(ServiceRegistrar lookupProxy)
                                                         throws RemoteException
    {
        LookupLocator loc = QAConfig.getConstrainedLocator(lookupProxy.getLocator());
        /* If at least one of the registrations in the registrationMap is
         * configured to discover the given lookup service by locator, then
         * return true; otherwise return false.
         */
	Iterator iter = registrationMap.values().iterator();
        for(int i=0;iter.hasNext();i++) {
            LDSEventListener listener = (LDSEventListener)iter.next();
            RegistrationInfo regInfo  = listener.getRegInfo();
            for(int j=0;j<regInfo.getLocatorsToDiscover().length;j++) {
                if( locsEqual(loc,regInfo.getLocatorsToDiscover()[j]) ) return true;
            }//end loop(j)
        }//end loop(iter)
        return false;
    }//end discoverByLocAnyReg
    
    /**
     * Administratively replaces the current set of groups in which the lookup
     * service (referenced by the <code>proxy</code> parameter) is a member
     * with a new set of member groups represented by the group names 
     * contained in the <code>groups</code> parameter.
     *
     * @param proxy   instance of the proxy to the lookup service whose
     *                current member groups are to be replaced
     * @param groups  String array whose elements are the names of the 
     *                member groups with which to replace the current
     *                member groups of the lookup service. If 
     *                <code>null</code> is input, no attempt will be
     *                made to replace the lookup service's current
     *                set of member groups. 
     *
     * @return <code>true</code> if the lookup service's set of member
     *         groups was successfully replaced; <code>false</code> otherwise.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the proxy and the
     *         service's backend. When this exception does occur, the
     *         set of groups to which the lookup service is a member may or
     *         may not have been successfully replaced.
     * 
     * @see net.jini.admin.Administrable
     * @see net.jini.admin.Administrable#getAdmin
     * @see net.jini.lookup.DiscoveryAdmin
     * @see net.jini.lookup.DiscoveryAdmin#setMemberGroups
     */
    protected boolean setLookupMemberGroups(ServiceRegistrar proxy,
                                                String[] groups)
                                                        throws RemoteException, TestException
    {
        if( (proxy == null) || (groups == null) ) return false;
        /* First, test that the lookup service implements both of the
         * appropriate administration interfaces
         */
        DiscoveryAdmin discoveryAdmin = null;
        if( !(proxy instanceof Administrable) ) return false;
        Object admin = ((Administrable)proxy).getAdmin();
//	try {
            admin = config.prepare("test.reggieAdminPreparer", admin);
//	} catch (TestException e) {
//	    throw new RemoteException("Preparation error", e);
//	}
        if( !(admin instanceof DiscoveryAdmin) ) return false;
        discoveryAdmin = (DiscoveryAdmin)admin;
        /* Set the member groups for the lookup service */
        discoveryAdmin.setMemberGroups(groups);
        return true;
    }//end setLookupMemberGroups
  
        /** Determines if the given locators are equivalent.
     *
     *  This method is a convenience method that is called instead of calling
     *  only the <code>equals</code> method on <code>LookupLocator</code>.
     *  This is necessary because the <code>equals</code> method on
     *  <code>LookupLocator</code> performs a simple <code>String</code>
     *  compare of the host names referenced by the locators being compared.
     *  Such a comparison can result in a "false negative" when the hostname
     *  associated with one locator is a fully-qualified hostname
     *  (ex. "myhost.subdomain.mycompany.com"), but the hostname of the
     *  locator with which the first locator is being compared is only the
     *  unqualified hostname (ex. "myhost"). In this case, both host names
     *  are legal and functionally equivalent, but the <code>equals</code>
     *  method on <code>LookupLocator</code> will interpret them as unequal.
     *
     *  To address the problem described above, this method will do the 
     *  following when attempting to determine whether the given locators
     *  are equivalent:
     *
     *    1. Apply <code>LookupLocator</code>.<code>equals</code> to determine
     *       if the given locators are actually 'equal'.
     *    2. If <code>LookupLocator</code>.<code>equals</code> method returns
     *       <code>false</code>, then retrieve and compare the port and
     *       <code>InetAddress</code> of each element to the respective 
     *       ports and <code>InetAddress</code>s of the given locators.
     * 
     * @return <code>true</code> if the given set of locators contains the
     *         given locator; <code>false</code> otherwise.
     */
    public static boolean locsEqual(LookupLocator loc0, LookupLocator loc1) {
        if( loc0.equals(loc1) ) return true;
        if( loc0.getPort() != loc1.getPort() ) return false;
        try {
            InetAddress addr0 = InetAddress.getByName(loc0.getHost());
            InetAddress addr1 = InetAddress.getByName(loc1.getHost());
            if( addr0.equals(addr1) ) return true;
        } catch(Exception e) {
            logger.log(Level.WARNING, "problem retrieving address by name", e);
            return false;
        }
        return false;
    }//end locsEqual
    
    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  augments that registration's desired groups with the given set of
     *  groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void addGroupsAllRegs(String[] groups) throws RemoteException {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            addGroupsOneReg( groups, (Map.Entry)iter.next() );
        }//end loop
        if( groups == DiscoveryGroupManagement.ALL_GROUPS ) {
            logger.log(Level.FINE, "   FAILURE -- tried to add ALL_GROUPS");
        } else if( groups.length <= 0 ) {
            logger.log(Level.FINE, "   NO-OP -- tried to add NO_GROUPS");
        } else {
            GroupsUtil.displayGroupSet(groups,
                                       "   all regs -- group",Level.FINE);
        }//endif
    }//end addGroupsAllRegs
       /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method augments that registration's desired groups
     *  with the given set of groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void addGroupsOneReg(String[]  groups,
                                   Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        String[] groupsToDiscover = groups;
        if(   (regInfo.getGroupsToDiscover() != DiscoveryGroupManagement.ALL_GROUPS)
            &&(groups != DiscoveryGroupManagement.ALL_GROUPS) )
       {
            int curLen = (regInfo.getGroupsToDiscover()).length;
            int newLen = groups.length;
            groupsToDiscover = new String[curLen + newLen];
            for(int i=0;i<curLen;i++) {
                groupsToDiscover[i] = new String(regInfo.getGroupsToDiscover()[i]);
            }//end loop
            for(int i=curLen;i<(curLen+newLen);i++) {
                groupsToDiscover[i] = new String(groups[i-curLen]);
            }//end loop
            synchronized(regInfo) {
                /* update the expected state for this regInfo information */
                regInfo.setLookupsToDiscover(groupsToDiscover,
                                             new LookupLocator[0]);
            }//end sync(regInfo)
        }//endif
        /* Add to the groups the lds should discover for this registration */
        reg.addGroups(groups);
    }//end addGroupsOneReg
    
   /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  replaces that registration's desired groups with the given set of
     *  groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void setGroupsAllRegs(String[] groups) throws RemoteException {
        Set eSet = getRegistrationMap().entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            setGroupsOneReg( groups, (Map.Entry)iter.next() );
        }//end loop
        if( groups == DiscoveryGroupManagement.ALL_GROUPS ) {
            logger.log(Level.FINE, "   all regs -- groups set to ALL_GROUPS");
        } else if( groups.length <= 0 ) {
            logger.log(Level.FINE, "   all regs -- groups set to NO_GROUPS");
        } else {
            GroupsUtil.displayGroupSet(groups,
                                       "   all regs -- group",Level.FINE);
        }//endif
    }//end setGroupsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method replaces that registration's desired groups
     *  with the given set of groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void setGroupsOneReg(String[]  groups,
                                   Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        synchronized(regInfo) {
            /* update the expected state for this regInfo information */
            regInfo.setLookupsToDiscover(groups,new LookupLocator[0]);
        }//end sync(regInfo)
        /* Change the groups the lds should discover for this registration */
        reg.setGroups(groups);
    }//end setGroupsOneReg

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  removes the given set of groups from that registration's desired
     *  groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void removeGroupsAllRegs(String[] groups) throws RemoteException{
        Iterator iter = (registrationMap.entrySet()).iterator();
        for(int i=0;iter.hasNext();i++) {
            removeGroupsOneReg( groups, (Map.Entry)iter.next() );
        }//end loop
        if( groups == DiscoveryGroupManagement.ALL_GROUPS ) {
            logger.log(Level.FINE, "   FAILURE -- tried to remove ALL_GROUPS");
        } else if( groups.length <= 0 ) {
            logger.log(Level.FINE, "   NO-OP -- tried to remove NO_GROUPS");
        } else {
            GroupsUtil.displayGroupSet(groups,
                              "   all regs removing -- group",Level.FINE);
        }//endif
    }//end removeGroupsAllRegs
    
    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method removes the given set of groups from that
     *  registration's desired groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void removeGroupsOneReg(String[]  groups,
                                      Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();

        String[] curGroupsToDiscover = regInfo.getGroupsToDiscover();
        String[] groupsToRemove = groups;
        String[] newGroupsToDiscover = DiscoveryGroupManagement.NO_GROUPS;

        if(    (curGroupsToDiscover != DiscoveryGroupManagement.ALL_GROUPS)
            && (groupsToRemove      != DiscoveryGroupManagement.ALL_GROUPS) )
        {
            int curLen = curGroupsToDiscover.length;
            int remLen = groupsToRemove.length;
            ArrayList newList = new ArrayList(curLen+remLen);
            iLoop:
            for(int i=0;i<curLen;i++) {
                for(int j=0;j<remLen;j++) {
                    if(curGroupsToDiscover[i].equals(groupsToRemove[j])) {
                        continue iLoop;
                    }//endif
                }//endloop(j)
                newList.add(curGroupsToDiscover[i]);
            }//end loop(i)
            newGroupsToDiscover
                   = (String[])(newList.toArray(new String[newList.size()]));
            synchronized(regInfo) {
                /* update the expected state for this regInfo information */
                regInfo.setLookupsToDiscover(newGroupsToDiscover,
                                             new LookupLocator[0]);
            }//end sync(regInfo)
        }//endif
        /* remove the groups the lds should discover from this registration */
        reg.removeGroups(groups);
    }//end removeGroupsOneReg

    /** Convenience method that allows sub-classes of this class to request,
     *  for the registrations referenced in the given regMap, the removal
     *  of the given set of groups from each registration.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void removeGroupsRegMap(Map regMap, String[] groups)
                                                       throws RemoteException
    {
        Set eSet = regMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            removeGroupsOneReg( groups, regListenerPair );
            if( groups.length <= 0 ) {
                logger.log(Level.FINE, "   NO-OP -- tried to remove NO_GROUPS");
            } else {
                RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
                GroupsUtil.displayGroupSet( groups,
                                               "   registration_"
                                               +regInfo.getHandback()
                                               +" removing -- group",
                                               Level.FINE);
            }//endif
        }//end loop
    }//end removeGroupsRegMap

    
    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  augments that registration's desired locators with the given set of
     *  locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void addLocatorsAllRegs(LookupLocator[] locators)
                                                      throws RemoteException
    {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            addLocatorsOneReg( locators, (Map.Entry)iter.next() );
        }//end loop
        if( locators.length <= 0 ) {
            logger.log(Level.FINE, "   NO-OP -- tried to add NO_LOCATORS");
        } else {
            LocatorsUtil.displayLocatorSet(locators,
                                      "   all regs -- locator",Level.FINE);
        }//endif
    }//end addLocatorsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method augments that registration's desired locators
     *  with the given set of locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void addLocatorsOneReg(LookupLocator[] locators,
                                     Map.Entry       regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        LookupLocator[] locatorsToDiscover = locators;

        if(locators != null) {
            int curLen = (regInfo.getLocatorsToDiscover()).length;
            int newLen = locators.length;
            locatorsToDiscover = new LookupLocator[curLen + newLen];
            for(int i=0;i<curLen;i++) {
                locatorsToDiscover[i] = regInfo.getLocatorsToDiscover()[i];
            }//end loop
            for(int i=curLen;i<(curLen+newLen);i++) {
                locatorsToDiscover[i] = locators[i-curLen];
            }//end loop
        }//endif
        synchronized(regInfo) {
            /* update the expected state for this regInfo information */
            regInfo.setLookupsToDiscover(DiscoveryGroupManagement.NO_GROUPS,
                                         locatorsToDiscover);
        }//end sync(regInfo)
        /* Add to the locators the lds should discover for this registration */
        reg.addLocators(locators);
    }//end addLocatorsOneReg

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  replaces that registration's desired locators with the given set of
     *  locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void setLocatorsAllRegs(LookupLocator[] locators)
                                                      throws RemoteException
    {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            setLocatorsOneReg( locators, (Map.Entry)iter.next() );
        }//end loop
        if( locators.length == 0 ) {
            logger.log(Level.FINE, "   all regs -- locators set to NO_LOCATORS");
        } else {
            LocatorsUtil.displayLocatorSet(locators,
                                      "   all regs -- locator",Level.FINE);
        }//endif
    }//end setLocatorsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method replaces that registration's desired locators
     *  with the given set of locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void setLocatorsOneReg(LookupLocator[]  locators,
                                     Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        synchronized(regInfo) {
            /* update the expected state for this regInfo information */
            regInfo.setLookupsToDiscover(DiscoveryGroupManagement.NO_GROUPS,
                                         locators);
        }//end sync(regInfo)
        /* Change the locators the lds should discover for this registration */
        reg.setLocators(locators);
    }//end setLocatorsOneReg
    
    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  removes the given set of locators from that registration's desired
     *  locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void removeLocatorsAllRegs(LookupLocator[] locators)
                                                       throws RemoteException
    {
        Iterator iter = (registrationMap.entrySet()).iterator();
        for(int i=0;iter.hasNext();i++) {
            removeLocatorsOneReg( locators, (Map.Entry)iter.next() );
        }//end loop
        if( locators == null ) {
            logger.log(Level.FINE, "   FAILURE -- tried to remove null");
        } else if( locators.length == 0 ) {
            logger.log(Level.FINE, "   NO-OP -- tried to remove NO_LOCATORS");
        } else {
            LocatorsUtil.displayLocatorSet(locators,
                            "   all regs removing -- locator",Level.FINE);
        }//endif
    }//end removeLocatorsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method removes the given set of locators from that
     *  registration's desired locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void removeLocatorsOneReg(LookupLocator[]  locators,
                                        Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();

        LookupLocator[] curLocatorsToDiscover = regInfo.getLocatorsToDiscover();
        LookupLocator[] locatorsToRemove = locators;
        LookupLocator[] newLocatorsToDiscover = new LookupLocator[0];

        if( (curLocatorsToDiscover != null) && (locatorsToRemove != null) ) {
            int curLen = curLocatorsToDiscover.length;
            int remLen = locatorsToRemove.length;
            ArrayList newList = new ArrayList(curLen+remLen);
            iLoop:
            for(int i=0;i<curLen;i++) {
                for(int j=0;j<remLen;j++) {
                    if( locsEqual
                             (curLocatorsToDiscover[i],locatorsToRemove[j]) )
                    {
                        continue iLoop;
                    }//endif
                }//endloop(j)
                newList.add(curLocatorsToDiscover[i]);
            }//end loop(i)
            newLocatorsToDiscover = (LookupLocator[])(newList.toArray
                                         (new LookupLocator[newList.size()]));
            synchronized(regInfo) {
                /* update the expected state for this regInfo information */
                regInfo.setLookupsToDiscover
                                          (DiscoveryGroupManagement.NO_GROUPS,
                                           newLocatorsToDiscover);
            }//end sync(regInfo)
        }//endif
        /* remove the locators the lds should discover for this registration */
        reg.removeLocators(locators);
    }//end removeLocatorsOneReg

    /** Convenience method that allows sub-classes of this class to request,
     *  for the registrations referenced in the given regMap, the removal
     *  of the given set of locators from each registration.
     *  
     *  @throws jave.rmi.RemoteException
     */
    public void removeLocatorsRegMap(Map regMap,
                                        LookupLocator[] locators)
                                                       throws RemoteException
    {
        Set eSet = regMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            removeLocatorsOneReg( locators, regListenerPair );
            if( locators.length == 0 ) {
                logger.log(Level.FINE, "   NO-OP -- tried to remove NO_LOCATORS");
            } else {
                RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
                LocatorsUtil.displayLocatorSet( locators,
                                               "   registration_"
                                               +regInfo.getHandback()
                                               +" removing -- locator",
                                               Level.FINE);
            }//endif
        }//end loop
    }//end removeLocatorsRegMap
    
    /** Convenience method that returns a shallow copy of the
     *  <code>lookupList</code> <code>ArrayList</code> that contains the
     *  proxies to the lookup services that have been started so far.
     *  The size of that list is retrieved while the list is locked, 
     *  so that the list is not modified while the copy is being made.
     */
    public List<ServiceRegistrar> getLookupListSnapshot() {
        return getLookupListSnapshot(null);
    }//end getLookupListSnapshot

    public List<ServiceRegistrar> getLookupListSnapshot(String infoStr) {
        String str = ( (infoStr == null) ? 
                       "     sync on lookupList --> " :
                       "     "+infoStr+" - sync on lookupList --> ");
        if(debugsync) logger.log(Level.FINE, "{0}requested", str);
        synchronized(lookupList) {
            if(debugsync) logger.log(Level.FINE, "{0}granted", str);
            List<ServiceRegistrar> listSnapshot = new ArrayList<ServiceRegistrar>(lookupList.size());
            for(int i=0;i<lookupList.size();i++) {
                listSnapshot.add(i,lookupList.get(i));
            }//end loop
            if(debugsync) logger.log(Level.FINE, "{0}released", str);
            return listSnapshot;
        }//end sync(lookupList)
    }//end getLookupListSnapshot

    /** Convenience method that returns the current size of the
     *  <code>lookupList</code> <code>ArrayList</code> that contains the
     *  proxies to the lookup services that have been started so far.
     *  The size of that list is retrieved while the list is locked, 
     *  so that the list is not modified while the retrieval is being made.
     */
    public int curLookupListSize() {
        return curLookupListSize(null);
    }//end curLookupListSize

    public int curLookupListSize(String infoStr) {
        String str = ( (infoStr == null) ? 
                       "     sync on lookupList --> " :
                       "     "+infoStr+" - sync on lookupList --> ");
        if(debugsync) logger.log(Level.FINE, str+"requested");
        synchronized(lookupList) {
            if(debugsync) logger.log(Level.FINE, str+"granted");
            int size = lookupList.size();
            if(debugsync) logger.log(Level.FINE, str+"released");
            return size;
        }//end sync(lookupList)
    }//end curLookupListSize
  
    /** Returns the proxy to each lookup service started (already prepared)*/
    protected ServiceRegistrar[] getLookupProxies() {
        Set proxySet = genMap.keySet();
        Object [] proxyArray = proxySet.toArray();
        int l = proxyArray.length;
        ServiceRegistrar[] proxies = new ServiceRegistrar[l];
        for(int i=0;i<l;i++) {
            Object curObj = proxyArray[i];
            if(curObj instanceof DiscoveryProtocolSimulator) {
                proxies[i]
                      = ((DiscoveryProtocolSimulator)curObj).getLookupProxy();
            } else {
                proxies[i] = (ServiceRegistrar)curObj;
            }//endif
        }//end loop
        return proxies;
    }//end getLookupProxies

    /** For each lookup service corresponding to an element of the global
     *  HashMap 'genMap', this method stops the generation of multicast
     *  announcements by either destroying the lookup service, or 
     *  explicitly stopping the announcements and then destroying the
     *  lookup service.
     *  
     *  @throws com.sun.jini.qa.harness.TestException
     */
    public boolean terminateAllLookups() throws TestException {
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            Object curObj = iter.next();
            ServiceRegistrar regProxy = null;
            if(curObj instanceof DiscoveryProtocolSimulator) {
                DiscoveryProtocolSimulator curGen
                                         = (DiscoveryProtocolSimulator)curObj;
                regProxy = curGen.getLookupProxy();
                curGen.stopAnnouncements();
            } else {
                regProxy = (ServiceRegistrar)curObj;
            }//endif
            /* destroy lookup service i */
            manager.destroyService(regProxy);
        }//end loop
        return true;
    }//end terminateAllLookups

    /** This method stops the generation of multicast announcements from each
     *  lookup service that has been started. The announcements are stopped
     *  directly if possible or, if stopping the announcements directly is not
     *  possible, the announcements are stopped indirectly by destroying each
     *  lookup service (ex. reggie does not allow one to stop its multicast
     *  announcements while allowing the service to remain running and
     *  reachable.)
     */
    public boolean stopAnnouncements() {
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            logger.log(Level.FINE, " stop multicast announcements "
                              +"from lookup service "+i+" ...");
            Object curObj = iter.next();
            if(curObj instanceof DiscoveryProtocolSimulator) {
                DiscoveryProtocolSimulator curGen
                                         = (DiscoveryProtocolSimulator)curObj;
                curGen.stopAnnouncements();
            } else {//cannot stop the announcements, must destroy the lookup
                /* It's not a simulated LUS, thus the only way to stop the
                 * announcements is to destroy each LUS individually.
                 */
                manager.destroyService((ServiceRegistrar)curObj);
            }//endif
        }//end loop
        return true;
    }//end stopAnnouncements

    /** For each lookup service proxy contained in the input array, this
     *  method first determines if that lookup service is reachable by
     *  attempting to retrieve the associated locator; that is, it
     *  attempts to 'ping' the lookup service. The lookup services found
     *  to be un-reachable are then used to update the given listener's
     *  expected discard state. After updating the listener's expected 
     *  state, each unreachable lookup service is discarded from the given
     *  instance of LookupDiscovery.
     * 
     *  This method returns an ArrayList containing LocatorGroupsPair
     *  instances corresponding to the lookups that were NOT discarded.
     *  This is so that the expected discard event state can be built
     *  correctly (a discarded event should not be expected for a lookup
     *  service that couldn't be discarded).
     */
    protected List pingAndDiscard(ServiceRegistrar[] proxies,
                                       DiscoveryManagement dm,
                                       LookupListener listener)
    {
        List proxiesToDiscard      = new LinkedList();
        List locGroupsNotDiscarded = new LinkedList();
        /* Determine proxies to discard and proxies that cannot be discarded */
        int l = proxies.length;
        for(int i=0;i<l;i++) {
            LocatorGroupsPair curPair
                      = (LocatorGroupsPair)regsToLocGroupsMap.get(proxies[i]);
            try {
                LookupLocator loc = QAConfig.getConstrainedLocator(proxies[i].getLocator());
                logger.log(Level.FINE, " ");
                if(curPair != null) {
                    logger.log(Level.FINE,
                                      " warning -- lookup service "
                                      +"is still reachable --> locator = "
                                      +curPair.getLocator()+"\n");
                    locGroupsNotDiscarded.add(curPair);
                } else {
                    logger.log(Level.FINE,
                                      " warning -- lookup service "+i
                                      +" is still reachable\n");
                }//endif
            } catch(RemoteException e) {//lookup is un-reachable, discard it
                proxiesToDiscard.add(proxies[i]);
            }
        }//end loop
        /* Perform the actual discards to generate the discard events */
        for(int i=0;i<proxiesToDiscard.size();i++) {
            dm.discard((ServiceRegistrar)proxiesToDiscard.get(i));
        }//end loop
        return locGroupsNotDiscarded;//return proxies we couldn't discard
    }//end pingAndDiscard

    /**
     * @return the nLookupServices
     */
    public int getnLookupServices() {
        return nLookupServices;
    }

    /**
     * @return the nAddLookupServices
     */
    public int getnAddLookupServices() {
        return nAddLookupServices;
    }

    /**
     * @return the nRemoteLookupServices
     */
    public int getnRemoteLookupServices() {
        return nRemoteLookupServices;
    }

    /**
     * @return the nAddRemoteLookupServices
     */
    public int getnAddRemoteLookupServices() {
        return nAddRemoteLookupServices;
    }

    /**
     * @return the nSecsServiceDiscovery
     */
    public int getnSecsServiceDiscovery() {
        return nSecsServiceDiscovery;
    }

    /**
     * @return the nSecsJoin
     */
    public int getnSecsJoin() {
        return nSecsJoin;
    }

    /**
     * @return the nServices
     */
    public int getnServices() {
        return nServices;
    }

    /**
     * @return the nAddServices
     */
    public int getnAddServices() {
        return nAddServices;
    }

    /**
     * @return the nAttributes
     */
    public int getnAttributes() {
        return nAttributes;
    }

    /**
     * @return the nAddAttributes
     */
    public int getnAddAttributes() {
        return nAddAttributes;
    }

    /**
     * @return the remoteHost
     */
    public String getRemoteHost() {
        return remoteHost;
    }

    /**
     * @return the allLookupsToStart
     */
    public List<LocatorGroupsPair> getAllLookupsToStart() {
        synchronized (lock){
            List<LocatorGroupsPair> list = new ArrayList<LocatorGroupsPair>(allLookupsToStart.size());
            list.addAll(allLookupsToStart);
            return list;
        }
    }

    /**
     * @return the regsToLocGroupsMap
     */
    public ConcurrentMap<ServiceRegistrar,LocatorGroupsPair> getRegsToLocGroupsMap() {
        return regsToLocGroupsMap;
    }

    /**
     * @return the genMap
     */
    public ConcurrentMap<Object, String[]> getGenMap() {
        return genMap;
    }

    /**
     * @return the initLookupsToStart
     */
    public List<LocatorGroupsPair> getInitLookupsToStart() {
        List<LocatorGroupsPair> lgp = new LinkedList<LocatorGroupsPair>();
        synchronized (lock){
            lgp.addAll(initLookupsToStart);
            return lgp;
        }
    }

    /**
     * @return the addLookupsToStart
     */
    public List<LocatorGroupsPair> getAddLookupsToStart() {
        List<LocatorGroupsPair> lgp = new LinkedList<LocatorGroupsPair>();
        synchronized (lock){
            lgp.addAll(addLookupsToStart);
            return lgp;
        }
    }

    /**
     * @return the lookupsStarted
     */
    public List<LocatorGroupsPair> getLookupsStarted() {
        List<LocatorGroupsPair> lgp = new LinkedList<LocatorGroupsPair>();
        synchronized (lookupsStarted){
            lgp.addAll(lookupsStarted);
            return lgp;
        }
    }

    /**
     * @return the originalAnnounceInterval
     */
    public int getOriginalAnnounceInterval() {
        return originalAnnounceInterval;
    }

    /**
     * @param nAddServices the nAddServices to set
     */
    public void setnAddServices(int nAddServices) {
        this.nAddServices = nAddServices;
    }
    
    /**
     * Returns a thread in which a number of lookup services are started after
     * various time delays. This thread is intended to be used by tests that need to
     * simulate "late joiner" lookup services. After all of the requested
     * lookup services have been started, this thread will exit.
     * 
     * The thread doesn't start until Thread.start() is called.
     * @return Thread that staggers starting of lookup services.
     */
    public Thread staggeredStartThread(int index){
        return new StaggeredStartThread(index);
    }

    /**
     * @return the announceInterval
     */
    public int getAnnounceInterval() {
        return announceInterval;
    }

    /**
     * @return the registrationMap
     */
    public ConcurrentMap<Object,RemoteEventListener> getRegistrationMap() {
        return registrationMap;
    }

    /**
     * @return the minNAnnouncements
     */
    public int getMinNAnnouncements() {
        return minNAnnouncements;
    }

    /**
     * @return the nIntervalsToWait
     */
    public int getnIntervalsToWait() {
        return nIntervalsToWait;
    }

    /**
     * @return the useGroupAndLocDiscovery0
     */
    public List<DiscoveryStruct> getUseGroupAndLocDiscovery0() {
        return useGroupAndLocDiscovery0;
    }

    /**
     * @return the useGroupAndLocDiscovery1
     */
    public List<DiscoveryStruct> getUseGroupAndLocDiscovery1() {
        return useGroupAndLocDiscovery1;
    }

    /**
     * @return the useOnlyGroupDiscovery
     */
    public List<DiscoveryStruct> getUseOnlyGroupDiscovery() {
        return useOnlyGroupDiscovery;
    }

    /**
     * @return the useOnlyLocDiscovery
     */
    public List<DiscoveryStruct> getUseOnlyLocDiscovery() {
        return useOnlyLocDiscovery;
    }

    public boolean lookupListContains(ServiceRegistrar s){
        synchronized (lookupList){
            return lookupList.contains(s);
        }
    }
    
    /** Thread in which a number of lookup services are started after various
     *  time delays. This thread is intended to be used by tests that need to
     *  simulate "late joiner" lookup services. After all of the requested
     *  lookup services have been started, this thread will exit.
     */
    private class StaggeredStartThread extends Thread {
        private final long[] waitTimes
                           = {    5*1000, 10*1000, 20*1000, 30*1000, 60*1000, 
                               2*60*1000,
                                 60*1000, 30*1000, 20*1000, 10*1000, 5*1000 };
        private final int startIndx;

        /** Use this constructor if a number of lookup services (equal to the
         *  value of the given startIndx) have already been started; and this
         *  thread will start the remaining lookup services. The locGroupsList
         *  parameter is an ArrayList that should contain LocatorGroupsPair
         *  instances that reference the locator and corresponding member
         *  groups of each lookup service to start.
         */
         private StaggeredStartThread(int startIndx) {
            super("StaggeredStartThread");
            setDaemon(true);
            this.startIndx = startIndx;
        }//end constructor
         
        private int size(){
            synchronized (lock){
                return allLookupsToStart.size();
            }
        }

        public void run() {
            int n = waitTimes.length;
            for(int i=startIndx;((!isInterrupted())&&(i<size()));
                                                                          i++)
            {
                long waitMS = ( i < n ? waitTimes[i] : waitTimes[n-1] );
                logger.log(Level.FINE,
                              " waiting "+(waitMS/1000)+" seconds before "
                              +"attempting to start the next lookup service");
                try { 
                    Thread.sleep(waitMS);
                } catch(InterruptedException e) { 
                    /* Need to re-interrupt this thread because catching
                     * an InterruptedException clears the interrupted status
                     * of this thread. 
                     * 
                     * If the sleep() call was not interrupted but was timed
                     * out, this means that this thread should continue
                     * processing; and the fact that the interrupted status
                     * has been cleared is consistent with that fact. On the
                     * other hand, if the sleep() was actually interrupted,
                     * this means that some entity external to this thread
                     * is signalling that this thread should exit. But the
                     * code below that determines whether to exit or continue
                     * processing bases its decision on the state of the
                     * interrupted status. And since the interrupted status
                     * was cleared when the InterruptedException was caught,
                     * the interrupted status of this thread needs to be reset
                     * to an interrupted state so that an exit will occur.
                     */
                    Thread.currentThread().interrupt();
                }
                
                synchronized (lock){
                    LocatorGroupsPair pair = allLookupsToStart.get(i);
                    LookupLocator l = pair.getLocator();
                    int port = l.getPort();
                    if(portInUse(port)) port = 0;
                    if( isInterrupted() )  break;//exit this thread
                    try {
                        startLookup(i, port, l.getHost());
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                    if(port == 0) refreshLookupLocatorListsAt(i);
                }
            }//end loop
        }//end run
    }//end class StaggeredStartThread


}
