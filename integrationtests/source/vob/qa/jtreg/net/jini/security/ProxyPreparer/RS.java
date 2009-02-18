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

import javax.security.auth.Subject;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;

/** Class that implements RemoteMethodControl */
class RS extends UnitTestUtilities implements RemoteMethodControl {
    final int i;
    private final MethodConstraints constraints;

    RS(int i, MethodConstraints constraints) {
	this.i = i;
	this.constraints = constraints;
    }

    public MethodConstraints getConstraints() {
	return constraints;
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new RS(i, constraints);
    }

    public String toString() {
	return getClass().getName() + "{" + i + ", " + constraints + "}";
    }

    protected String className() {
	return "RS";
    }

    public int hashCode() {
	return i + (constraints == null ? 0 : constraints.hashCode());
    }

    public boolean equals(Object o) {
	if (o instanceof RS) {
	    RS rs = (RS) o;
	    return i == rs.i && safeEquals(constraints, rs.constraints);
	}
	return false;
    }

    /** Class that is trusted by Verifier */
    static class Trusted extends RS {
	Trusted(int i, MethodConstraints constraints) {
	    super(i, constraints);
	}

	public RemoteMethodControl setConstraints(
	    MethodConstraints constraints)
	{
	    return new Trusted(i, constraints);
	}
    }

    /** Class that causes Verifier to throw RemoteException */
    static class VerifyThrows extends RS {
	VerifyThrows(int i, MethodConstraints constraints) {
	    super(i, constraints);
	}

	public RemoteMethodControl setConstraints(
	    MethodConstraints constraints)
	{
	    return new VerifyThrows(i, constraints);
	}
    }
}

