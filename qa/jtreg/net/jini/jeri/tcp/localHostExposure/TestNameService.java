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
import java.net.InetAddress;
import java.net.UnknownHostException;
import sun.net.spi.nameservice.NameService;

public class TestNameService implements NameService {

    private static final Object lock = new Object();
    private static String lastNameLookup = null;

    static String getLastNameLookup() {
	synchronized (lock) {
	    return lastNameLookup;
	}
    }
    
    /* Java 6 version */
    public InetAddress [] lookupAllHostAddr(String host) throws UnknownHostException{
        byte [][] allHostAdd = lookAllHostAddr(host);
        int l = allHostAdd.length;
        InetAddress [] result = new InetAddress[l];
        for (int i = 0; i<l; i++){
            result[i] = InetAddress.getByAddress(allHostAdd[i]);
        }
        return result;
    }

    private byte[][] lookAllHostAddr(String host)
	throws UnknownHostException
    {
	// System.err.println("FORWARD: " + host);
	synchronized (lock) {
	    lastNameLookup = host;
	}
	throw new UnknownHostException(host);
    }

    public String getHostByAddr(byte[] addr) throws UnknownHostException {
	// System.err.println("REVERSE: " + addrToString(addr));
	throw new UnknownHostException(addrToString(addr));
    }

    private static String addrToString(byte[] addr) {
	return
	    (addr[0] & 0xFF) + "." +
	    (addr[1] & 0xFF) + "." +
	    (addr[2] & 0xFF) + "." +
	    (addr[3] & 0xFF);
    }
}
