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
package net.jini.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import org.apache.river.action.GetBooleanAction;

/**
 * A holder class for constants that pertain to the unicast and
 * multicast discovery protocols.
 * <p>
 * The following properties if set, provide support for IPv6:
 * <li>net.jini.discovery.IPv6=TRUE
 * <li>net.jini.discovery.GLOBAL_ANNOUNCE=TRUE
 * <p>
 * Note that support for global announcement must be specifically set to
 * be enabled.  There is no support for global request, for obvious reasons.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class Constants {
    
    /**
     * If true IPv6 has been enabled.
     */
    public static final Boolean IPv6 = AccessController.doPrivileged(
	    new GetBooleanAction("net.jini.discovery.IPv6"));
    
    /**
     * If true and IPv6 is also true, the announcement protocol will 
     * propagate over global networks.
     */
    public static final Boolean GLOBAL_ANNOUNCE = AccessController.doPrivileged(
	    new GetBooleanAction("net.jini.discovery.GLOBAL_ANNOUNCE"));
    /**
     * The address of the multicast group over which the multicast
     * request protocol takes place.
     */
    private static InetAddress requestAddress = null;

    /**
     * The address of the multicast group over which the multicast
     * announcement protocol takes place.
     */
    private static InetAddress announcementAddress = null;
    
    private static final Object LOCK = new Object();

    /**
     * The port for both unicast and multicast boot requests.
     */
    public static final short discoveryPort = 4160;

    /**
     * This class cannot be instantiated.
     */
    private Constants() {}
  
    /**
     * Return the address of the multicast group over which the
     * multicast request protocol takes place.
     * @return the address of the multicast group over which the
     *         multicast request protocol takes place
     * @throws UnknownHostException if no IP address for the host could
     * be found.
     */
    public static final InetAddress getRequestAddress()
	throws UnknownHostException
    {
        synchronized (LOCK){
            if (requestAddress != null) return requestAddress;
	    if (IPv6) {
		requestAddress = InetAddress.getByName("FF05::156");
	    } else {
		requestAddress = InetAddress.getByName("224.0.1.85");
	    }
            return requestAddress;
        }
    }
  

    /**
     * Return the address of the multicast group over which the
     * multicast announcement protocol takes place.
     * @return the address of the multicast group over which the
     *         multicast announcement protocol takes place.
     * @throws UnknownHostException if no IP address for the host could
     * be found.
     */
    public static final InetAddress getAnnouncementAddress()
	throws UnknownHostException
    {
        synchronized (LOCK){
            if (announcementAddress != null) return announcementAddress;
	    if (IPv6){
		if (GLOBAL_ANNOUNCE) {
		    announcementAddress = InetAddress.getByName("FF0X::155");
		} else {
		    announcementAddress = InetAddress.getByName("FF05::155");
		}
	    }
            announcementAddress = InetAddress.getByName("224.0.1.84");
            return announcementAddress;
        }
    }
}
