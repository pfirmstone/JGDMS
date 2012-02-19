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

package org.apache.river.api.delegates;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketPermission;
import java.net.UnknownHostException;
import java.security.Guard;
import javax.net.SocketFactory;

/**
 * A wrapper class that uses wrapper classes that implement Li Gong's method
 * guard pattern for Sockets.
 * 
 * For implementations that require dynamic or temporary Permissions for
 * Principals and client code can grant a DelegatePermission that encapsulates
 * a SocketPermission allowing temporary access to sockets.
 * 
 * This is experimental and untested, the public API will not change, however
 * the implementation has been provided to generate more use, auditing and
 * bug fixing.
 * 
 * It is here, more to demonstrate the use of DelegatePermission.
 * 
 * TODO: write some jtreg or qa harness tests.
 * 
 * @author Peter Firmstone.
 */
public class DelegateSocketFactory extends SocketFactory{
    
    private final SocketFactory rep;
    
    DelegateSocketFactory(SocketFactory factory){
        rep = factory;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        Socket soc = rep.createSocket(host, port);
        Guard g = DelegatePermission.get(new SocketPermission( host +":" +port, "CONNECT"));
        return new DelegateSocket(soc, g);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        Socket soc = rep.createSocket(host, port, localHost, localPort);
        Guard g = DelegatePermission.get(new SocketPermission( host +":" +port, "CONNECT"));
        return new DelegateSocket(soc, g);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        Socket soc = rep.createSocket(host, port);
        Guard g = DelegatePermission.get(new SocketPermission( host +":" +port, "CONNECT"));
        return new DelegateSocket(soc, g);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket soc = rep.createSocket(address, port, localAddress, localPort);
        Guard g = DelegatePermission.get(new SocketPermission( address +":" +port, "CONNECT"));
        return new DelegateSocket(soc, g);
    }
    
}
