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
package com.sun.jini.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// java.lang.reflect
import java.lang.reflect.InvocationHandler;

// java.rmi
import java.rmi.Remote;
import java.rmi.server.ExportException;


/**
 * Class implementing Exporter interface to be used as mainExporter
 * for ProxyTrustExporter.
 */
public class ValidMainExporter extends BaseExporter {

    // proxy produced by 'export' method
    protected Remote proxy;

    /**
     * Default constructor.
     */
    public ValidMainExporter() {
        proxy = null;
    }

    /**
     * Creates proxy implementing RemoteMethodControl and TrustEquivalence
     * interfaces. Increase number of this method invocations by one.
     *
     * @param impl implementation for proxy
     * @return proxy implementing RemoteMethodControl and TrustEquivalence
     *         interfaces
     */
    public Remote export(Remote impl) throws ExportException {
        ++expNum;
        proxy = (Remote) ProxyTrustUtil.newProxyInstance(new RMCTEImpl());
        return proxy;
    }

    /**
     * Method from Exporter interface. Does nothing.
     *
     * @return false
     */
    public boolean unexport(boolean force) {
        return false;
    }

    /**
     * Returns proxy produced by 'export' method.
     *
     * @return proxy produced by 'export' method
     */
    public Object getProxy() {
        return proxy;
    }
}
