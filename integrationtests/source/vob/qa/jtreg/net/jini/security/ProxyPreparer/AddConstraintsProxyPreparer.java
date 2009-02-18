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
import java.lang.reflect.Method;
import java.security.Permission;
import java.util.Iterator;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.security.BasicProxyPreparer;

/**
 * A basic proxy preparer that adds constraints to the existing constraints on
 * the proxy.
 */
public class AddConstraintsProxyPreparer extends BasicProxyPreparer {

    public AddConstraintsProxyPreparer(boolean verify, 
				       MethodConstraints methodConstraints,
				       Permission[] permissions)
    {
	super(verify, methodConstraints, permissions);
    }

    protected MethodConstraints getMethodConstraints(Object proxy) {
	return proxy instanceof RemoteMethodControl
	    ? new CombinedConstraints(
		((RemoteMethodControl) proxy).getConstraints(),
		methodConstraints)
	    : null;
    }

    public static class CombinedConstraints implements MethodConstraints {
	private final MethodConstraints x;
	private final MethodConstraints y;

	public CombinedConstraints(MethodConstraints x, MethodConstraints y) {
	    if (x == null || y == null) {
		throw new NullPointerException("Arguments cannot be null");
	    }
	    this.x = x;
	    this.y = y;
	}

	public InvocationConstraints getConstraints(Method method) {
	    return InvocationConstraints.combine(x.getConstraints(method),
					       y.getConstraints(method));
	}

	public Iterator possibleConstraints() {
	    final Iterator i = x.possibleConstraints();
	    final Iterator j = y.possibleConstraints();
	    return new Iterator() {
		public boolean hasNext() {
		    return i.hasNext() || j.hasNext();
		}
		public Object next() {
		    return i.hasNext() ? i.next() : j.next();
		}
		public void remove() {
		    throw new UnsupportedOperationException();
		}
	    };
	}

	public boolean equals(Object obj) {
	    if (this == obj) {
		return true;
	    } else if (!(obj instanceof CombinedConstraints)) {
		return false;
	    }
	    CombinedConstraints other = (CombinedConstraints) obj;
	    return (x.equals(other.x) && y.equals(other.y)) ||
		(x.equals(other.y) && y.equals(other.x));
	}

	public int hashCode() {
	    return x.hashCode() + y.hashCode();
	}
    }
}
