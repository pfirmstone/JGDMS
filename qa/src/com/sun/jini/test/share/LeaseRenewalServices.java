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

import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author peter
 */
public class LeaseRenewalServices {
    /** the logger */
    private static final Logger logger = Logger.getLogger("com.sun.jini.qa.harness");
    private final int nLeaseRenewalServices;
    private final int nAddLeaseRenewalServices;
    private final int nRemoteLeaseRenewalServices;
    private final int nAddRemoteLeaseRenewalServices;
    
    public LeaseRenewalServices(QAConfig config){
        int testType = config.getIntConfigVal("com.sun.jini.testType",
                                   BaseQATest.AUTOMATIC_LOCAL_TEST);
        /* begin lease renewal service info */
        int nLeaseRenewalServ = config.getIntConfigVal
                           ("net.jini.lease.nLeaseRenewalServices", 0);
        int nRemoteLeaseRenewalServ = config.getIntConfigVal
                         ("net.jini.lease.nRemoteLeaseRenewalServices", 0);
        int nAddLeaseRenewalServ = config.getIntConfigVal
                           ("net.jini.lease.nAddLeaseRenewalServices", 0);
        int nAddRemoteLeaseRenewalServ = config.getIntConfigVal
                      ("net.jini.lease.nAddRemoteLeaseRenewalServices", 0);
        if(testType == BaseQATest.MANUAL_TEST_REMOTE_COMPONENT) {
            nLeaseRenewalServ = nRemoteLeaseRenewalServ;
            nAddLeaseRenewalServ = nAddRemoteLeaseRenewalServ;
            nRemoteLeaseRenewalServ = 0;
            nAddRemoteLeaseRenewalServ = 0;
        }//endif
        this.nLeaseRenewalServices = nLeaseRenewalServ;
        this.nRemoteLeaseRenewalServices = nRemoteLeaseRenewalServ;
        this.nAddLeaseRenewalServices = nAddLeaseRenewalServ;
        this.nAddRemoteLeaseRenewalServices = nAddRemoteLeaseRenewalServ;
        
        int tmpN =   nLeaseRenewalServ+ nAddLeaseRenewalServ
               + nRemoteLeaseRenewalServ + nAddRemoteLeaseRenewalServ;
        if(tmpN > 0) {
            logger.log(Level.FINE, " ----- Lease Renewal Service Info ----- ");
            logger.log(Level.FINE, " # of lease renewal services to start     -- {0}", nLeaseRenewalServ);
            logger.log(Level.FINE, " # of additional lease renewal srvcs      -- {0}", nAddLeaseRenewalServ);
        }//endif(tmpN > 0)
        /* Handle remote/local components of manual tests */
        String remoteHost = config.getStringConfigVal("net.jini.lookup.remotehost",
                                            "UNKNOWN_HOST");
        switch(testType) {
            case BaseQATest.MANUAL_TEST_REMOTE_COMPONENT:
                logger.log(Level.FINE, " ***** REMOTE COMPONENT OF A MANUAL TEST "+"(remote host = {0}) ***** ", remoteHost);
                break;
            case BaseQATest.MANUAL_TEST_LOCAL_COMPONENT:
                logger.log(Level.FINE, " ***** LOCAL COMPONENT OF A MANUAL TEST "+"(remote host = {0}) ***** ", remoteHost);
                logger.log(Level.FINE, " ----- Remote Lease Renewal Service Info ----- ");
                logger.log(Level.FINE, " # of remote lease renewal services    -- {0}", nRemoteLeaseRenewalServices);
                logger.log(Level.FINE, " additional remote lease renewal srvcs -- {0}", nAddRemoteLeaseRenewalServices);
                break;
        }//end switch(testType)
    }

    /**
     * @return the nLeaseRenewalServices
     */
    public int getnLeaseRenewalServices() {
        return nLeaseRenewalServices;
    }

    /**
     * @return the nAddLeaseRenewalServices
     */
    public int getnAddLeaseRenewalServices() {
        return nAddLeaseRenewalServices;
    }

    /**
     * @return the nRemoteLeaseRenewalServices
     */
    public int getnRemoteLeaseRenewalServices() {
        return nRemoteLeaseRenewalServices;
    }

    /**
     * @return the nAddRemoteLeaseRenewalServices
     */
    public int getnAddRemoteLeaseRenewalServices() {
        return nAddRemoteLeaseRenewalServices;
    }
    
}
