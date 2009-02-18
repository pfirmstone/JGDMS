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
package com.sun.jini.test.spec.javaspace.conformance;

import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.TrustVerifier;

import java.io.Serializable;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.List;
import java.rmi.RemoteException;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.proxy.BasicProxyTrustVerifier;


public class TestEventListener05 implements RemoteEventListener,
                                            ServerProxyTrust, Serializable
{

    private static Configuration configuration;

    private Object proxy;

    private ArrayList notifications = new ArrayList();

    public TestEventListener05() throws RemoteException {
        Configuration c = getConfiguration();
        Exporter exporter = QAConfig.getDefaultExporter();
        if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
            try {
                exporter = (Exporter) c.getEntry("test",
                                                 "outriggerListenerExporter",
                                                 Exporter.class);
            } catch (ConfigurationException e) {
                throw new RemoteException("Configuration Error", e);
            }
        }
        proxy = exporter.export(this);
    }

    public void notify(RemoteEvent theEvent) throws UnknownEventException,
                                                    java.rmi.RemoteException
    {
        notifications.add(theEvent);
    }

    public List getNotifications() {
        return notifications;
    }

    public Object writeReplace() throws ObjectStreamException {
        return proxy;
    }

    public TrustVerifier getProxyVerifier() {
        return new BasicProxyTrustVerifier(proxy);
    }

    public static void setConfiguration(Configuration configuration) {
        TestEventListener05.configuration = configuration;
    }

    public static Configuration getConfiguration() {
        if (TestEventListener05.configuration == null) {
            throw new IllegalStateException("Configuration not set");
        }
        return TestEventListener05.configuration;
    }

}
