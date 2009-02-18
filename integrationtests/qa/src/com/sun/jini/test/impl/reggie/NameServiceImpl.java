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
package com.sun.jini.test.impl.reggie;

import java.net.UnknownHostException;
import java.util.Arrays;
import sun.net.spi.nameservice.NameService;

import java.net.InetAddress;

/**
 * Name service implementation 
 */
public class NameServiceImpl implements NameService {

    private static final String testClient = "testClient";
    private static final byte[] addr1 = new byte[] { (byte)0, 0, 0, 0 };
    private static final byte[] addr2 = 
        new byte[] { (byte)255, (byte)255, (byte)255, (byte)255};
    private static final String localhost = "localhost";
    private static final byte[] localhostAddr = new byte[]{127,0,0,1};

    public NameServiceImpl() {

    }

    public InetAddress[] lookupAllHostAddr(String host)
	throws UnknownHostException
    {
        if (host.equalsIgnoreCase(testClient)) {
            return ( new InetAddress[] 
                       { InetAddress.getByAddress(addr1),
                         InetAddress.getByAddress(addr2),
                         InetAddress.getByAddress(localhostAddr) } );
        } else if (host.equalsIgnoreCase(localhost)) {
            return ( new InetAddress[] 
                       { InetAddress.getByAddress(localhostAddr) } );
        } else {
	    throw new UnknownHostException(host);
        }
    }

    public String getHostByAddr(byte[] addr) throws UnknownHostException {
        if (Arrays.equals(addr, addr1) || Arrays.equals(addr, addr2))
        {
            return testClient;
        } else if (Arrays.equals(addr, localhostAddr)) {
            return localhost;
	} else {
	    throw new UnknownHostException(addrToString(addr));
	}
    }

    private static String addrToString(byte[] addr) {
	return
	    (addr[0] & 0xFF) + "." +
	    (addr[1] & 0xFF) + "." +
	    (addr[2] & 0xFF) + "." +
	    (addr[3] & 0xFF);
    }
}
