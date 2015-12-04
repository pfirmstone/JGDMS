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
package org.apache.river.test.spec.jeri.basicjeritrustverifier.util;

//jeri imports
import net.jini.security.TrustVerifier.Context;

//java.util
import java.util.Collection;
import java.util.ArrayList;

//java.rmi
import java.rmi.RemoteException;

/**
 * Dummy trust verifier context used in BasicJeriTrustVerifier tests
 */
public class TestTrustVerifierCtx implements Context {

    private boolean trust = false;
    private ClassLoader loader = null;
    private RemoteException exception = null;

    public TestTrustVerifierCtx(boolean trust, ClassLoader loader){
        this.trust = trust;
        this.loader = loader;
    }

    public TestTrustVerifierCtx(boolean trust, ClassLoader loader,
        RemoteException exception){
        this.trust = trust;
        this.loader = loader;
        this.exception = exception;
    }

    //inherit javadoc
    public Collection getCallerContext() {
        return new ArrayList();
    }

    //inherit javadoc
    public ClassLoader getClassLoader() {
        return loader;
    }

    //inherit javadoc
    public boolean isTrustedObject(Object obj) throws RemoteException {
        if (exception!=null) {
            throw exception;
        }
        return trust;
    }

}
