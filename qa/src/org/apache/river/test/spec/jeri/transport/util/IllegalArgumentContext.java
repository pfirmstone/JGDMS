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
package org.apache.river.test.spec.jeri.transport.util;

//jeri imports
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;

//java.io
import java.io.IOException;

//java.util
import java.util.ArrayList;
import java.util.logging.Logger;

public class IllegalArgumentContext implements ListenContext{

    private ArrayList cookies = new ArrayList();
    private ArrayList useCookies = null;
    private int cookieIndex = 0;
    private ArrayList endpoints = new ArrayList();

    public IllegalArgumentContext(ArrayList useCookies){
         this.useCookies = useCookies;
    }

    public synchronized ListenCookie addListenEndpoint(ListenEndpoint endpoint)
        throws IOException {

        Logger log = AbstractEndpointTest.getLogger();
        RequestDispatcher rd = new SETRequestHandler();
        ListenHandle lh = endpoint.listen(rd);
        log.finest("The endpoint returned " + lh);
        ListenHandle returnHandle = lh;
        cookies.add(cookieIndex,lh);
        if (useCookies!=null){
            returnHandle = (ListenHandle) useCookies.get(cookieIndex);
            log.finest("Returning invalid cookie " + returnHandle);
        }
        cookieIndex++;
        endpoints.add(new EndpointHolder(endpoint,returnHandle,rd));
        return returnHandle.getCookie();
    }

    public synchronized ArrayList getCookies() {
        return cookies;
    }

    public synchronized ArrayList getEndpoints() {
        return endpoints;
    }

}
