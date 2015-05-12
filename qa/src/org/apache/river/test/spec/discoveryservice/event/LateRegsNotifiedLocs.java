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

package org.apache.river.test.spec.discoveryservice.event;

import java.util.logging.Level;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * This class verifies that, upon registration, so-called "late-joiner"
 * registrations that request discovery of locators that have already been
 * discovered for other registration(s) will receive notification of all
 * of those previously discovered lookup services. That is, this class
 * verifies that when one or more registrations -- configured for discovery
 * of a set of locators -- are added to a lookup discovery service after
 * an initial set of registration(s) -- similarly configured for
 * locator discovery -- have been made with that lookup discovery service, the
 * second set of registrations are notified of the discovery of all lookup
 * services having the locators with which the registraions were configured. 
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to either a finite set
 *        of member groups, or no groups (groups membership is irrelevant
 *        for this test)
 *   <li> one instance of the lookup discovery service
 *   <li> a set of one or more initial registrations with the lookup
 *        discovery service, each configured for discovery of the
 *        locator(s) of those lookup service(s)
 *   <li> each initial registration with the lookup discovery service should
 *        receive remote discovery events through an instance of 
 *        RemoteEventListener
 *   <li> after verification that the initial registrations have received
 *        the appropriate notifications, an additional set of one or more
 *        registrations with the lookup discovery service, each configured
 *        for discovery of the same locator(s) as the initial set
 *   <li> each additional registration should also receive remote discovery
 *        events through an instance of RemoteEventListener
 * </ul><p>
 * 
 * If the lookup discovery service functions as specified, then upon
 * requesting the additional registrations, the listener of each such
 * registration will receive an instance of  <code>RemoteDiscoveryEvent</code>
 * which accurately reflects the expected lookup services.
 */
public class LateRegsNotifiedLocs extends LateRegsNotifiedGroups {

    /** Performs actions necessary to prepare for execution of the 
     *  current test. Populates the sets of group and/or locators to use.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        logger.log(Level.FINE, "setup()");
        groupsToDiscover = getGroupsToDiscover(getUseOnlyLocDiscovery());
        locsToDiscover = getLocatorsToDiscover(getUseOnlyLocDiscovery());
        return this;
    }//end construct

} //end class LateRegsNotifiedLocs

