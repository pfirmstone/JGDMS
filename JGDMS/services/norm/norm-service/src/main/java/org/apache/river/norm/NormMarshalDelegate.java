/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.norm;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.MarshalDelegate;

/**
 * {@link MarshalDelegate} for {@code org.apache.river.norm} (the norm service's
 * package-private {@code @AtomicSerial} state classes).
 *
 * <p>Serves all three {@code @AtomicSerial} classes of this package -- all
 * package-private, concrete, and stateful (none {@code @Stateless}) -- by
 * dispatching their {@code serialForm}/{@code serialize}/{@code (GetArg)} members
 * in-package, with no reflection.  {@code CreateLeaseSet}'s {@code (GetArg)}
 * constructor is {@code protected}; it is reachable here because the delegate is
 * in the same package.
 *
 * @see MarshalDelegate
 */
public final class NormMarshalDelegate implements MarshalDelegate {

    /** Public no-arg constructor required by the service-provider discovery. */
    public NormMarshalDelegate() { }

    @Override
    public SerialForm[] serialForm(Class<?> c) {
        if (c == ClientLeaseWrapper.class) return ClientLeaseWrapper.serialForm();
        if (c == CreateLeaseSet.class)     return CreateLeaseSet.serialForm();
        if (c == LeaseSet.class)           return LeaseSet.serialForm();
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public void serialize(Class<?> c, PutArg arg, Object o) throws IOException {
        if (c == ClientLeaseWrapper.class) { ClientLeaseWrapper.serialize(arg, (ClientLeaseWrapper) o); return; }
        if (c == CreateLeaseSet.class)     { CreateLeaseSet.serialize(arg, (CreateLeaseSet) o); return; }
        if (c == LeaseSet.class)           { LeaseSet.serialize(arg, (LeaseSet) o); return; }
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public Object create(Class<?> c, GetArg arg) throws IOException, ClassNotFoundException {
        if (c == ClientLeaseWrapper.class) return new ClientLeaseWrapper(arg);
        if (c == CreateLeaseSet.class)     return new CreateLeaseSet(arg);
        if (c == LeaseSet.class)           return new LeaseSet(arg);
        throw new IllegalArgumentException(unhandled(c));
    }

    private static String unhandled(Class<?> c) {
        return "NormMarshalDelegate does not serve " + c.getName()
                + "; it serves only the @AtomicSerial classes of org.apache.river.norm";
    }
}
