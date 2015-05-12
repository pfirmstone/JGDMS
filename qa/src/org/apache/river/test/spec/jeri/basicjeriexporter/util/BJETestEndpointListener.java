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
package org.apache.river.test.spec.jeri.basicjeriexporter.util;

//overture imports
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.Endpoint;

//java.lang.reflect
import java.lang.reflect.Method;

/**
 * This class implements an instrumented server enpoint listener
 */
public class BJETestEndpointListener implements ServerEndpoint.ListenHandle{

    private ServerEndpoint.ListenHandle listener;

    public BJETestEndpointListener(ServerEndpoint.ListenHandle listener) {
        this.listener = listener;
    }

    public void close() {
        TransportListener testListener = BJETransportListener.getListener();
        if (testListener!=null){
            try {
                Method m = this.getClass().getMethod("close", new Class[] {});
                testListener.called(m,this,new Object[] {});
            } catch (NoSuchMethodException e){
                BJETransportListener.getUtilLog().warning("Something is"
                    +" really wrong - a method from this class is not"
                    + "found in this class: " + e.getMessage());
                e.printStackTrace();
            }
        }
        listener.close();
    }

    public ServerEndpoint.ListenCookie getCookie() {
        return listener.getCookie();
    }
}
