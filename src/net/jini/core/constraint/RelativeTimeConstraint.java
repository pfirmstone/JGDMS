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

package net.jini.core.constraint;

/**
 * Implemented by constraints that are expressed in terms of relative time,
 * to support conversion to absolute time constraints.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface RelativeTimeConstraint extends InvocationConstraint {
    /**
     * Converts this constraint to absolute time.  Takes an absolute time,
     * specified in milliseconds from midnight, January 1, 1970 UTC, and
     * returns a constraint that has the same information content as this
     * constraint except that the relative times have been converted to
     * absolute times by adding the specified absolute time to each relative
     * time in this constraint. If the addition results in underflow or
     * overflow, a time value of <code>Long.MIN_VALUE</code> or
     * <code>Long.MAX_VALUE</code> is used, respectively. The returned
     * constraint will typically be an instance of a different constraint
     * class than this constraint.
     *
     * @param baseTime an absolute time, specified in milliseconds from
     * midnight, January 1, 1970 UTC
     * @return a constraint that has the relative times converted to absolute
     * times by adding the specified absolute time to each relative time
     */
    InvocationConstraint makeAbsolute(long baseTime);
}
