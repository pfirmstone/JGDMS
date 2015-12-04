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

//jeri imports
import net.jini.security.TrustVerifier.Context;

//java.util
import java.util.Collection;
import java.util.ArrayList;

//java.rmi
import java.rmi.RemoteException;

/**
 * Dummy trust verifier context used in SSLTrustVerifier tests
 */
public class TestTrustVerifierCtxSSL implements Context {

    private Throwable exception = null;

    public TestTrustVerifierCtxSSL(){
        this.exception = null;
    }

    public TestTrustVerifierCtxSSL(Throwable exception){
        this.exception = exception;
    }

    //inherit javadoc
    public Collection getCallerContext() {
        return new ArrayList();
    }

    //inherit javadoc
    public ClassLoader getClassLoader() {
        return null;
    }

    //inherit javadoc
    public boolean isTrustedObject(Object obj) throws RemoteException {
        if (exception!=null) {
            if (exception instanceof RemoteException) {
                throw (RemoteException) exception;
            } else if (exception instanceof SecurityException) {
                throw (SecurityException) exception;
            }
        }
        if (obj instanceof TestSocketFactory) {
            return ((TestSocketFactory)obj).getTrust();
        }
        return false;
    }

}
