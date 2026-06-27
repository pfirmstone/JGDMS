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

package org.apache.river.api.io;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import net.jini.io.ObjectStreamContext;

/**
 * Reference {@link AtomicSerial.GetArg} implementation backed by one
 * {@link ObjectInputStream.GetField} ({@link EmulatedFieldsForLoading}) per
 * {@code @AtomicSerial} class in the deserialized hierarchy.
 *
 * <p>Caller dispatch, per-{@code (caller, field)} memoization and all the typed
 * {@code get} accessors (now {@code final} and idempotent) live in the
 * {@link AtomicSerial.GetArg} base. This class supplies only:
 * <ul>
 *   <li>the {@link #lookup(Class, String)} value hook -- one untyped boxed read
 *       per field, called at most once by the memoizing base;</li>
 *   <li>the {@link #isDefaulted(Class, String)} presence hook -- decode-free,
 *       so {@code defaulted()} stays side-effect free;</li>
 *   <li>the impl-specific metadata methods {@code serialClasses()} and
 *       {@code getObjectStreamContext()}.</li>
 * </ul>
 *
 * <p>The old per-call caller resolution (a {@code SecurityManager} subclass
 * reading {@code getClassContext()[2]}) has been replaced by the base's
 * {@code StackWalker} dispatch, which both impls now share.
 *
 * @author peter
 */
class GetArgImpl extends AtomicSerial.GetArg {

    final Map<Class, ObjectInputStream.GetField> classFields;
    final ObjectInput in;

    GetArgImpl(Map<Class, ObjectInputStream.GetField> args, ObjectInput in) {
	super(); // protected GetArg() is a no-op since the 4.0.0 guard drop.
	classFields = args;
	this.in = in;
    }

    @Override
    protected Object lookup(Class<?> callerClass, String name) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(callerClass);
	if (fields == null) {
	    return ABSENT;
	}
	if (fields instanceof EmulatedFieldsForLoading) {
	    return ((EmulatedFieldsForLoading) fields).getBoxed(name, ABSENT);
	}
	// The @AtomicSerial path always supplies EmulatedFieldsForLoading; any
	// other GetField has no untyped accessor and is unexpected here.
	throw new IOException(
		"GetArgImpl: unsupported GetField implementation "
		+ fields.getClass().getName());
    }

    @Override
    protected boolean isDefaulted(Class<?> callerClass, String name) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(callerClass);
	return fields == null || fields.defaulted(name);
    }

    @Override
    public Collection getObjectStreamContext() {
	if (in instanceof ObjectStreamContext) {
	    return ((ObjectStreamContext) in).getObjectStreamContext();
	}
	return Collections.emptyList();
    }

    @Override
    public Class[] serialClasses() {
	return classFields.keySet().toArray(new Class[classFields.size()]);
    }
}
