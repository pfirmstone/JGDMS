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
package org.apache.river.test.spec.jeri.util;

import net.jini.jeri.ServerCapabilities;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.io.UnsupportedConstraintException;

/**
 * A fake ServerCapabilities object with configurable method return values.
 * <p>
 * Used by:
 * <ul>
 *   <li>org.apache.river.test.spec.jeri.basicilfactory.CreateInstancesTest
 *   <li>org.apache.river.test.spec.jeri.basicilfactory.CreateInvocationDispatcherTest
 *   <li>org.apache.river.test.spec.jeri.basicinvocationdispatcher.ConstructorTest
 * </ul>
 */
public class FakeServerCapabilities implements ServerCapabilities {

    private final InvocationConstraints constraints;

    /**
     * Constructs a FakeServerCapabilities.  
     */
    public FakeServerCapabilities(InvocationConstraint[] ic) {
        constraints = (ic == null ? InvocationConstraints.EMPTY : 
                                    new InvocationConstraints(ic,null));
    }

    /**
     * Implementation of interface method.
     */
    public InvocationConstraints checkConstraints(
        InvocationConstraints constraits) 
        throws UnsupportedConstraintException
    {
        return constraints;
    }

}
