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
package org.apache.river.test.share;

import org.apache.river.qa.harness.QAConfig;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author peter
 */
public class EventMailBoxServices {
    /** the logger */
    private static final Logger logger = Logger.getLogger("org.apache.river.qa.harness");
    private final int nEventMailboxServices;
    private final int nAddEventMailboxServices;
    private final int nRemoteEventMailboxServices;
    private final int nAddRemoteEventMailboxServices;
    
    public EventMailBoxServices(QAConfig config){
        int testType = config.getIntConfigVal("org.apache.river.testType",
                                   BaseQATest.AUTOMATIC_LOCAL_TEST);
        /* begin event mailbox service info */
        int nEventMailboxServ = config.getIntConfigVal
                           ("net.jini.event.nEventMailboxServices",
                             0);
        int nRemoteEventMailboxServ = config.getIntConfigVal
                         ("net.jini.event.nRemoteEventMailboxServices",
                           0);
        int nAddEventMailboxServ = config.getIntConfigVal
                           ("net.jini.event.nAddEventMailboxServices",
                             0);

        int nAddRemoteEventMailboxServ = config.getIntConfigVal
                      ("net.jini.event.nAddRemoteEventMailboxServices",
                       0);
        if(testType == BaseQATest.MANUAL_TEST_REMOTE_COMPONENT) {
            nEventMailboxServ = nRemoteEventMailboxServ;
            nAddEventMailboxServ = nAddRemoteEventMailboxServ;
            nRemoteEventMailboxServ = 0;
            nAddRemoteEventMailboxServ = 0;
        }//endif
        
        this.nEventMailboxServices = nEventMailboxServ;
        this.nAddEventMailboxServices = nAddEventMailboxServ;
        this.nRemoteEventMailboxServices = nRemoteEventMailboxServ;
        this.nAddRemoteEventMailboxServices = nAddRemoteEventMailboxServ;
        
        int tmpN =   nEventMailboxServ+ nAddEventMailboxServ
               + nRemoteEventMailboxServ + nAddRemoteEventMailboxServ;
        if(tmpN > 0) {
            logger.log(Level.FINE,
                          " ----- Event Mailbox Service Info ----- ");
            logger.log(Level.FINE, " # of event mailbox services to start     -- {0}", nEventMailboxServ);
            logger.log(Level.FINE, " # of additional event mailbox srvcs      -- {0}", nAddEventMailboxServ);
        }//endif(tmpN > 0)

        /* Handle remote/local components of manual tests */
        String remoteHost = config.getStringConfigVal("net.jini.lookup.remotehost",
                                            "UNKNOWN_HOST");
        switch(testType) {
            case BaseQATest.MANUAL_TEST_REMOTE_COMPONENT:
                logger.log(Level.FINE,
                                  " ***** REMOTE COMPONENT OF A MANUAL TEST "+"(remote host = {0}) ***** ", remoteHost);
                break;
            case BaseQATest.MANUAL_TEST_LOCAL_COMPONENT:
                logger.log(Level.FINE,
                                  " ***** LOCAL COMPONENT OF A MANUAL TEST "+"(remote host = {0}) ***** ", remoteHost);
                logger.log(Level.FINE,
                       " ----- Remote Event Mailbox Service Info ----- ");
                logger.log(Level.FINE, " # of remote event mailbox services    -- {0}", nRemoteEventMailboxServ);
                logger.log(Level.FINE, " additional remote event mailbox srvcs -- {0}", nAddRemoteEventMailboxServ);
                break;
        }//end switch(testType)

    }

    /**
     * @return the nEventMailboxServices
     */
    public int getnEventMailboxServices() {
        return nEventMailboxServices;
    }

    /**
     * @return the nAddEventMailboxServices
     */
    public int getnAddEventMailboxServices() {
        return nAddEventMailboxServices;
    }

    /**
     * @return the nRemoteEventMailboxServices
     */
    public int getnRemoteEventMailboxServices() {
        return nRemoteEventMailboxServices;
    }

    /**
     * @return the nAddRemoteEventMailboxServices
     */
    public int getnAddRemoteEventMailboxServices() {
        return nAddRemoteEventMailboxServices;
    }
}
