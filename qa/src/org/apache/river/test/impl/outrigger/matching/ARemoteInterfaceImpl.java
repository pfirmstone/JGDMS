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
package org.apache.river.test.impl.outrigger.matching;

import org.apache.river.proxy.BasicProxyTrustVerifier;
import org.apache.river.qa.harness.QAConfig;
import java.rmi.*;
import java.io.Serializable;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import java.io.ObjectStreamException;
import java.rmi.server.ExportException;

/**
 *
 * A Remote Interface
 *
 * @author John W. F. McClain
 */
public class ARemoteInterfaceImpl
    implements ARemoteInterface, ServerProxyTrust, Serializable
{
    private String rtn;
    private Object proxy;
    private Exporter exporter;

    public ARemoteInterfaceImpl(Configuration c, String s) throws RemoteException {
	Exporter exporter = QAConfig.getDefaultExporter();
	if (c instanceof org.apache.river.qa.harness.QAConfiguration) {
	    try {
		exporter = (Exporter) c.getEntry("test", 
					         "outriggerListenerExporter", 
					         Exporter.class);
	    } catch (ConfigurationException e) {
		throw new RemoteException("Bad configuration", e);
	    }
	}
	this.exporter = exporter;
        rtn = s;
    }
    
    public synchronized void export() throws ExportException {
        proxy = exporter.export(this);
    }

    public synchronized Object writeReplace() throws ObjectStreamException {
        return proxy;
    }

    public synchronized TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(proxy);
    }

    public String getAString() throws RemoteException {
        return rtn;
    }
}
