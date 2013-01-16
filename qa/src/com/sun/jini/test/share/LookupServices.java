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
import com.sun.jini.test.share.BaseQATest.LocatorGroupsPair;
import com.sun.jini.test.share.BaseQATest.LookupListener;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryManagement;
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
    
    private volatile int maxSecsEventWait = 600;
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
    private ConcurrentMap<ServiceRegistrar,LocatorGroupsPair> regsToLocGroupsMap = 
            new ConcurrentHashMap<ServiceRegistrar,LocatorGroupsPair>(11);

    LookupServices(QAConfig config, AdminManager manager, int fastTimeout) throws TestException{
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
    void startInitLookups() throws Exception {
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
    void startAddLookups() throws Exception {
        if(nAddLookupServices > 0) {
            /* Skip over remote lookups and lookups already started to the
             * indices of the additional local lookups
             */
            int n0 = nRemoteLookupServices + nAddRemoteLookupServices
                                           + lookupsStarted.size();
            int n1 = n0 + nAddLookupServices;
            synchronized (lock){
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
                                               (config,memberGroups, manager, port);
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
        logger.log(Level.FINE, "   memberGroup(s) = {0}", GroupsUtil.toCommaSeparatedStr(memberGroups));
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
        for(int i=0;i<lookupsStarted.size();i++) {
            LocatorGroupsPair pair = lookupsStarted.get(i);
            int curPort = (pair.getLocator()).getPort();
            if(port == curPort) return true;
        }//end loop
        return false;
    }//end portInUse
    
    private void refreshLookupLocatorListsAt(int index){
        LocatorGroupsPair locGroupsPair = lookupsStarted.get(index);
        /* index range of init lookups */
        int initLookupsBegin = nRemoteLookupServices + nAddRemoteLookupServices;
        int initLookupsEnd = initLookupsBegin + nLookupServices;
        /* index range of add lookups */
        int addLookupsBegin = nRemoteLookupServices + nAddRemoteLookupServices + nLookupServices;
        int addLookupsEnd = addLookupsBegin + nAddLookupServices;
        /* update lookup lists */
        if (index >= initLookupsBegin && index < initLookupsEnd) initLookupsToStart.set(index,locGroupsPair);
        if (index >= addLookupsBegin && index < addLookupsEnd) addLookupsToStart.set(index, locGroupsPair);
        allLookupsToStart.set(index,locGroupsPair);
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
     * @return 
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
     * @return  
     */
   public List<LocatorGroupsPair> replaceMemberGroups(boolean alternate) {
        List<LocatorGroupsPair> locGroupsList = new ArrayList<LocatorGroupsPair>(genMap.size());
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
    * @return  
    */
   public List<LocatorGroupsPair> replaceMemberGroups(int nReplacements,
                                           String[] newGroups)
   {
        List<LocatorGroupsPair> locGroupsList = new ArrayList<LocatorGroupsPair>(genMap.size());
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
        ServiceRegistrar[] proxies = new ServiceRegistrar[genMap.size()];
	Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            Object curObj = iter.next();
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
    protected ArrayList pingAndDiscard(ServiceRegistrar[] proxies,
                                       DiscoveryManagement dm,
                                       LookupListener listener)
    {
        ArrayList proxiesToDiscard      = new ArrayList(1);
        ArrayList locGroupsNotDiscarded = new ArrayList(1);
        /* Determine proxies to discard and proxies that cannot be discarded */
        for(int i=0;i<proxies.length;i++) {
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
     * @return the testType
     */
    public int getTestType() {
        return testType;
    }

    /**
     * @return the debugsync
     */
    public boolean isDebugsync() {
        return debugsync;
    }

    /**
     * @return the maxSecsEventWait
     */
    public int getMaxSecsEventWait() {
        return maxSecsEventWait;
    }

    /**
     * @return the announceInterval
     */
    public int getAnnounceInterval() {
        return announceInterval;
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
     * @return the nSecsLookupDiscovery
     */
    public int getnSecsLookupDiscovery() {
        return nSecsLookupDiscovery;
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
