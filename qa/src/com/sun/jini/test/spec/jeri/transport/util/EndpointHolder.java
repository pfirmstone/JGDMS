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
package com.sun.jini.test.spec.jeri.transport.util;

//jeri imports
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.RequestDispatcher;

public class EndpointHolder {

    private ServerEndpoint.ListenEndpoint le;
    private ServerEndpoint.ListenHandle lh;
    private RequestDispatcher rh;

    public EndpointHolder(ServerEndpoint.ListenEndpoint le,
        ServerEndpoint.ListenHandle lh, RequestDispatcher rh){
        this.le = le;
        this.lh = lh;
        this.rh = rh;
    }

    public ServerEndpoint.ListenEndpoint getListenEndpoint(){
        return le;
    }

    public ServerEndpoint.ListenHandle getListenHandle(){
        return lh;
    }

    public RequestDispatcher getRequestHandler(){
        return rh;
    }

}
