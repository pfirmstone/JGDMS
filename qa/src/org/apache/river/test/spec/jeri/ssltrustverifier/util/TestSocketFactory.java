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
package org.apache.river.test.spec.jeri.ssltrustverifier.util;

//javax.net
import javax.net.SocketFactory;

//java.net
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

//java.io
import java.io.IOException;

/**
 * Dummy socket factory for testing
 */
public class TestSocketFactory extends SocketFactory {

    private boolean trust = false;

    public TestSocketFactory(boolean trust) {
        this.trust = trust;
    }

    public boolean getTrust(){
        return trust;
    }

    //inherit javadoc
    public Socket createSocket(InetAddress address, int port)
        throws IOException {
        Socket s = createSocket();
        s.connect( new InetSocketAddress(address,port));
        return s;
    }

    //inherit javadoc
    public Socket createSocket(InetAddress address, int port,
        InetAddress localAddress, int localPort) throws IOException {
        Socket s = createSocket();
        s.bind(new InetSocketAddress(localAddress,localPort));
        s.connect(new InetSocketAddress(address,port));
        return s;
    }

    //inherit javadoc
    public Socket createSocket(String host, int port) throws IOException {
       Socket s = createSocket();
       s.connect(new InetSocketAddress(host,port));
       return s;
    }

    //inherit javadoc
    public Socket createSocket(String host, int port,
        InetAddress localHost, int localPort) throws IOException {
        Socket s = createSocket();
        s.bind(new InetSocketAddress(localHost,port));
        s.connect(new InetSocketAddress(host,port));
        return s;
    }
}
