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
package org.apache.river.test.spec.servicediscovery.discovery;

import java.util.logging.Level;

// java packages
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Collection;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

// Test harness packages
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;

// net.jini
import org.apache.river.qa.harness.Test;
import net.jini.discovery.IncomingMulticastAnnouncement;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.OutgoingMulticastAnnouncement;
import net.jini.core.lookup.ServiceID;


public class MulticastAnnouncement extends QATestEnvironment implements Test {

    private static String makeGroup(int length) {
        StringBuffer buf = new StringBuffer(length);

        for (int i = 0; i < length; i++) {
            buf.append(String.valueOf((char) ('a' + (i % 26))));
        }
        return buf.toString();
    }

    public static String[] makeGroups(int length, int count) {
        String[] array = new String[count];
        String group = makeGroup(length);

        for (int i = 0; i < count; i++) {
            array[i] = group + i;
        }
        return array;
    }

    public static ServiceID[] makeServiceIDs(long seed, int count) {
        ServiceID[] sids = new ServiceID[count];

        for (int i = 0; i < count; i++) {
            sids[i] = new ServiceID(seed + i, seed - i);
        }
        return sids;
    }

    public void run() throws Exception {
        ServiceID sid = new ServiceID(System.currentTimeMillis(), 235325);
        LookupLocator loc = QAConfig.getConstrainedLocator("yurt", 1010);
        String[] grps = makeGroups(200, 200);
        DatagramPacket[] reqs = null;
        Set grpSet = new HashSet();

        for (int i = 0; i < grps.length; i++) {
            grpSet.add(grps[i]);
        }

        // Try to force an exception by passing in a very long hostname.
        boolean excepted = false;

        try {
            String host = makeGroup(500);
            LookupLocator l = QAConfig.getConstrainedLocator(host, 2);
            reqs = OutgoingMulticastAnnouncement.marshal(sid,
                    l, makeGroups(20, 1));
            throw new TestException(
                    "marshal should have thrown an exception");
        } catch (IllegalArgumentException e) {
            logger.log(Level.INFO, 
		       "*** the next lines should complain about a host"
                     + " name being too long");
            e.printStackTrace();
        }

        // Try to force an exception by passing in a very long group name.

        try {
            reqs = OutgoingMulticastAnnouncement.marshal(sid, loc,
                    makeGroups(500, 1));
            throw new TestException(
                    "marshal should have thrown an exception");
        } catch (IllegalArgumentException e) {
            logger.log(Level.INFO, 
		       "*** the next lines should complain about a group name"
                    + " being too long");
            e.printStackTrace();
        }


        /*
         * Try marshalling with no groups at all.  We should get just
         * one packet back.
         */
	reqs = OutgoingMulticastAnnouncement.marshal(sid, loc, new String[0]);

        if (reqs == null) {
            throw new TestException(
                    "OutgoingMulticastAnnouncement.marshal returned null");
        }

        if (reqs.length != 1) {
            throw new TestException(
                    "OutgoingMulticastAnnouncement.marshal returned non-1"
                    + " array");
        }

	IncomingMulticastAnnouncement ann = 
	    new IncomingMulticastAnnouncement(reqs[0]);

	if (ann.getGroups().length != 0) {
	    throw new TestException("IncomingMulticastAnnouncement.getGroups "
				  + "returned non-empty array");
	}

        /*
         * Try marshalling a normal-looking announcement, so we can
         * ensure that it contains everything it should.
         */
	reqs = OutgoingMulticastAnnouncement.marshal(sid, loc, grps);

        if (reqs == null) {
            throw new TestException(
                    "OutgoingMulticastAnnouncement.marshal returned null");
        }

        if (reqs.length == 0) {
            throw new TestException(
                    "OutgoingMulticastAnnouncement.marshal returned empty"
                    + " array");
        }
        logger.log(Level.INFO, "Marshaled " + reqs.length + " packets");
        Collection marshaledGroups = new HashSet();

        for (int i = 0; i < reqs.length; i++) {
            if (reqs[i].getData().length > 512) {
                throw new TestException(
                        "request data is too large, at "
                        + reqs[i].getData().length + " bytes");
            }
            IncomingMulticastAnnouncement req = 
		new IncomingMulticastAnnouncement(reqs[i]);

            if (!req.getServiceID().equals(sid)) {
                throw new TestException(
                        "unmarshaled a bad service ID");
            }

            if (!QAConfig.getConstrainedLocator(req.getLocator()).equals(loc)) {
                throw new TestException(
                        "unmarshaled a bad locator");
            }
            String[] groupsU = req.getGroups();

            for (int j = 0; j < groupsU.length; j++) {
                if (!grpSet.contains(groupsU[j])) {
                    throw new TestException(
                            "unmarshaled a group we didn't marshal");
                }
                marshaledGroups.add(groupsU[j]);
            }
        }

        for (int i = 0; i < grps.length; i++) {
            if (!marshaledGroups.contains(grps[i])) {
                throw new TestException(
                        "failed to marshal a group");
            }
        }
    }

}
