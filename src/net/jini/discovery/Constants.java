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

/**
 * A holder class for constants that pertain to the unicast and
 * multicast discovery protocols.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class Constants {
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
     * @throws UnknownHostException 
     */
    public static final InetAddress getRequestAddress()
	throws UnknownHostException
    {
        synchronized (LOCK){
            if (requestAddress != null) return requestAddress;
            requestAddress = InetAddress.getByName("224.0.1.85");
            return requestAddress;
        }
    }
  

    /**
     * Return the address of the multicast group over which the
     * multicast announcement protocol takes place.
     * @return the address of the multicast group over which the
     *         multicast announcement protocol takes place.
     * @throws UnknownHostException
     */
    public static final InetAddress getAnnouncementAddress()
	throws UnknownHostException
    {
        synchronized (LOCK){
            if (announcementAddress != null) return announcementAddress;
            announcementAddress = InetAddress.getByName("224.0.1.84");
            return announcementAddress;
        }
    }
}
