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

package com.sun.jini.jeri.internal.http;

import java.io.IOException;
import java.net.Socket;

/**
 * Abstraction for objects which provide/configure sockets used by
 * HttpClientConnection instances.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public interface HttpClientSocketFactory {
    
    /**
     * Creates client socket connected to the given host and port.
     */
    Socket createSocket(String host, int port) throws IOException;
    
    /**
     * Creates layered socket on top of given base socket, for use when
     * tunneling HTTP messages through a proxy.
     */
    Socket createTunnelSocket(Socket s) throws IOException;
}
