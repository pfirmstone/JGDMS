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
package org.apache.river.test.spec.activation.util;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.rmi.Remote;
import java.rmi.RemoteException;
import javax.security.auth.Subject;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;


/**
 * A fake implementation of the <code>RemoteMethodControl</code>
 * interface.
 */
public class RMCProxy implements RemoteMethodControl, Remote {
    private Logger logger;

    public RMCProxy(Logger logger) {
        this.logger = logger;
    }

    public MethodConstraints getConstraints() {
        return null;
    }

    public MethodConstraints getServerConstraints() throws RemoteException {
        return null;
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
        return this;
    }
}
