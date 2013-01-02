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
package com.sun.jini.test.spec.servicediscovery.discovery;

import java.util.logging.Level;

// java packages
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Collection;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

// Test harness imports
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;

// net.jini
import com.sun.jini.qa.harness.Test;
import net.jini.discovery.OutgoingMulticastRequest;
import net.jini.discovery.IncomingMulticastRequest;
import net.jini.core.lookup.ServiceID;


public class MulticastRequest extends QATestEnvironment implements Test {

    private static String makeGroup(int length) {
        StringBuffer buf = new StringBuffer(length);

        for (int i = 0; i < length; i++) {
            buf.append(('a' + i) % 26);
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
        ServiceID[] sids = makeServiceIDs(System.currentTimeMillis(), 200);
        int port = (int) (1 + Math.abs(System.currentTimeMillis() % 65534));
        String[] grps = makeGroups(200, 200);
        DatagramPacket[] reqs = null;
        Set sidSet = new HashSet();

        for (int i = 0; i < sids.length; i++) {
            sidSet.add(sids[i]);
        }
        Set grpSet = new HashSet();

        for (int i = 0; i < grps.length; i++) {
            grpSet.add(grps[i]);
        }

        // Try to force an exception by giving an excessively long group name.

        try {
            reqs = OutgoingMulticastRequest.marshal(port, makeGroups(320, 1),
                    makeServiceIDs(port, 1));
            throw new TestException("marshal should have thrown an exception");
        } catch (IllegalArgumentException e) {
            logger.log(Level.INFO, 
		       "*** the next line should complain about a group name"
                     + " being too long");
            e.printStackTrace();
        }

        /*
         * Try to marshal the empty set of groups.  This should return
         * a single packet.
         */
	reqs = OutgoingMulticastRequest.marshal(port, new String[0], sids);

        if (reqs == null) {
            throw new TestException(
                    "OutgoingMulticastRequest.marshal returned null");
        }

        if (reqs.length != 1) {
            throw new TestException(
                    "OutgoingMulticastRequest.marshal returned non-1 array");
        }

        // Ensure that the single packet unmarshals sanely.
	IncomingMulticastRequest r = new IncomingMulticastRequest(reqs[0]);
	
	if (r.getGroups().length != 0) {
	    throw new TestException("IncomoingMulticastRequest.getGroups "
				  + "returned non-empty array");
	}

        /*
         * Try to marshal a normal-looking request, and ensure that it
         * checks out.
         */
	reqs = OutgoingMulticastRequest.marshal(port, grps, sids);

        if (reqs == null) {
            throw new TestException(
                    "OutgoingMulticastRequest.marshal returned null");
        }

        if (reqs.length == 0) {
            throw new TestException(
                    "OutgoingMulticastRequest.marshal returned empty array");
        }
        logger.log(Level.INFO, "Marshaled " + reqs.length + " packets");
        Collection marshaledGroups = new HashSet();

        for (int i = 0; i < reqs.length; i++) {
            if (reqs[i].getData().length > 512) {
                throw new TestException(
                        "request data is too large, at "
                        + reqs[i].getData().length + " bytes");
            }
            IncomingMulticastRequest req = new IncomingMulticastRequest(reqs[i]);

            if (req.getPort() != port) {
                throw new TestException(
                        "unmarshaled a bad port number");
            }
            String[] groupsU = req.getGroups();

            for (int j = 0; j < groupsU.length; j++) {
                if (!grpSet.contains(groupsU[j])) {
                    throw new TestException("unmarshaled a group we "
					  + "didn't marshal");
                }
                marshaledGroups.add(groupsU[j]);
            }
            ServiceID[] serviceIDsU = req.getServiceIDs();

            for (int j = 0; j < serviceIDsU.length; j++) {
                if (!sidSet.contains(serviceIDsU[j])) {
                    throw new TestException("unmarshaled a service ID "
					  + "we didn't marshal");
                }
            }
        }

        for (int i = 0; i < grps.length; i++) {
            if (!marshaledGroups.contains(grps[i])) {
                throw new TestException("failed to marshal some group");
            }
        }
    }

}
