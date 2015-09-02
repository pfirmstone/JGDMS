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
package org.apache.river.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// net.jini
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrust;


/**
 * Base class for all ProxyTrust classes implementing RemoteMethodControl and
 * ProxyTrust interfaces.
 */
public abstract class BaseProxyTrust extends BaseIsTrustedObjectClass
        implements RemoteMethodControl, ProxyTrust {

    /** MethodConstrains passed to 'setConstraints' method. */
    protected MethodConstraints mc = null;

    /** TrustVerifier created by getProxyVerifier method. */
    protected TrustVerifier tv = null;

    /**
     * Save incoming constraints to internal variable.
     *
     * @return this
     */
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
        mc = constraints;
        return this;
    }

    /**
     * Returns MethodConstraints passed to 'setConstraints' method.
     *
     * @return MethodConstraints passed to 'setConstraints' method
     */
    public MethodConstraints getConstraints() {
        return mc;
    }

    /**
     * Returns name of checked method.
     *
     * @return 'checked method' name
     */
    public String getMethodName() {
        return "getProxyVerifier";
    }
}
