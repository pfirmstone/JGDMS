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
import java.util.Arrays;
import sun.net.spi.nameservice.NameService;

public class TestNameService implements NameService {

    private static final String NAME_LOCALHOST = "localhost";
    private static final byte[] ADDR_127_0_0_1 = new byte[] { 127, 0, 0, 1 };

    private static final String NAME_FOO = "foo";
    private static final byte[] ADDR_1_1_1_1 = new byte[] { 1, 1, 1, 1 };
    private static final byte[] ADDR_2_2_2_2 = new byte[] { 2, 2, 2, 2 };

    private static final String endpointType = 
	System.getProperty("endpointType", "tcp");
    private static boolean isKerberos = false;
    private static String NAME_THISHOST = null;
    private static byte[] ADDR_THISHOST = null;
    private static String NAME_KDC = null;
    private static byte[] ADDR_KDC = null;
	    
    static {
	try {
	    if (endpointType.equals("kerberos")) {
		isKerberos = true;
		NAME_THISHOST = System.getProperty(
		    "com.sun.jini.jtreg.kerberos.multihome.hostname");
		NAME_KDC = System.getProperty("java.security.krb5.kdc");
		ADDR_THISHOST = InetAddress.getByName(
		    System.getProperty(
			"com.sun.jini.jtreg.kerberos.multihome.hostaddr"
		    )).getAddress();
		ADDR_KDC = InetAddress.getByName(
		    System.getProperty(
			"com.sun.jini.jtreg.kerberos.multihome.kdcaddr"
		    )).getAddress();
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    // do nothing
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

    /* Java 5 version of provider, renamed and privatised */
    private byte[][] lookAllHostAddr(String host)
	throws UnknownHostException
    {
	// System.err.println("FORWARD: " + host);
	if (host.equalsIgnoreCase(NAME_LOCALHOST)) {
	    return new byte[][] { ADDR_127_0_0_1 };
	} else if (host.equalsIgnoreCase(NAME_FOO)) {
	    return new byte[][] { ADDR_1_1_1_1, ADDR_2_2_2_2 };
	} else if (host.equalsIgnoreCase(NAME_FOO)) {
	    return new byte[][] { ADDR_1_1_1_1, ADDR_2_2_2_2 };
	} else if (host.equalsIgnoreCase(NAME_THISHOST)) {
	    return new byte[][] { ADDR_THISHOST };
	} else if (host.equalsIgnoreCase(NAME_KDC)) {
	    return new byte[][] { ADDR_KDC };
	} else {
	    throw new UnknownHostException(host);
	}
    }

    public String getHostByAddr(byte[] addr) throws UnknownHostException {
	// System.err.println("REVERSE: " + addrToString(addr));
	if (Arrays.equals(addr, ADDR_127_0_0_1)) {
	    return NAME_LOCALHOST;
	} else if (Arrays.equals(addr, ADDR_THISHOST) && isKerberos) {
	    return NAME_THISHOST;
	} else if (Arrays.equals(addr, ADDR_KDC) && isKerberos) {
	    return NAME_KDC;
	} else if (Arrays.equals(addr, ADDR_1_1_1_1) ||
		   Arrays.equals(addr, ADDR_2_2_2_2))
	{
	    return NAME_FOO;
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
