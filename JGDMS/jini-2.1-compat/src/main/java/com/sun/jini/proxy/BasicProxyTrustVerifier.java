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
package com.sun.jini.proxy;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;

/**
 * Provided for backward compatibility, migrate to new name space.
 * 
 */
@Deprecated
public final class BasicProxyTrustVerifier implements TrustVerifier, Serializable {
    private static final long serialVersionUID = 2L;
    
    /**
     * The trusted proxy.
     *
     * @serial
     */
    private final RemoteMethodControl proxy;
    
    private transient org.apache.river.proxy.BasicProxyTrustVerifier tv;
    
    public BasicProxyTrustVerifier(Object proxy) {
        tv = new org.apache.river.proxy.BasicProxyTrustVerifier(proxy);
        this.proxy = (RemoteMethodControl) proxy;
    }

    @Override
    public boolean isTrustedObject(Object obj, Context ctx) throws RemoteException {
        return tv.isTrustedObject(obj, ctx);
    }
    
    
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	tv = new org.apache.river.proxy.BasicProxyTrustVerifier(proxy);
    }
}
