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

package net.jini.jeri;

import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;

/**
 * Represents the constraint support capabilities of a server-side
 * transport layer implementation.
 *
 * <p>The {@link #checkConstraints checkConstraints} method is
 * intended to be used by an {@link InvocationDispatcher} to verify
 * support for constraints.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface ServerCapabilities {

    /**
     * Verifies that this instance supports the transport layer
     * aspects of all of the specified requirements (both in general
     * and in the current security context), and returns the
     * requirements that must be at least partially implemented by
     * higher layers in order to fully satisfy all of the specified
     * requirements.  This method may also return preferences that
     * must be at least partially implemented by higher layers in
     * order to fully satisfy some of the specified preferences.
     *
     * <p>For any given constraint, there must be a clear delineation
     * of which aspects (if any) must be implemented by the transport
     * layer.  This method must not return a constraint (as a
     * requirement or a preference, directly or as an element of
     * another constraint) unless this instance can implement all of
     * those aspects.  Also, this method must not return a constraint
     * for which all aspects must be implemented by the transport
     * layer.  Most of the constraints in the {@link
     * net.jini.core.constraint} package must be fully implemented by
     * the transport layer and thus must not be returned by this
     * method; the one exception is {@link Integrity}, for which the
     * transport layer is responsible for the data integrity aspect
     * and higher layers are responsible for the code integrity
     * aspect.
     *
     * <p>For any {@link ConstraintAlternatives} in the specified
     * constraints, this method should only return a corresponding
     * constraint if all of the alternatives supported by this
     * instance need to be at least partially implemented by higher
     * layers in order to be fully satisfied.
     *
     * <p>The constraints passed to this method may include
     * constraints based on relative time.
     *
     * @param constraints the constraints that must be supported
     *
     * @return the constraints that must be at least partially
     * implemented by higher layers
     *
     * @throws UnsupportedConstraintException if the transport layer
     * aspects of any of the specified requirements are not supported
     * by this instance (either in general or in the current security
     * context)
     *
     * @throws SecurityException if the current security context does
     * not have the permissions necessary to perform this operation
     *
     * @throws NullPointerException if <code>constraints</code> is
     * <code>null</code>
     **/
    InvocationConstraints checkConstraints(InvocationConstraints constraints)
	throws UnsupportedConstraintException;
}
