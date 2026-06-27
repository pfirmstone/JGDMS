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

package org.apache.river.lookup.util;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.MarshalDelegate;

/**
 * {@link MarshalDelegate} for {@code org.apache.river.lookup.util}.
 *
 * <p>This package mixes public and non-public {@code @AtomicSerial} classes, so
 * the delegate serves <em>only</em> the one that needs in-package dispatch:
 * {@code ConsistentMapEntry}, a package-private final class.  The public
 * {@code ConsistentSet} (public class, public {@code (GetArg)} constructor) needs
 * no delegate and is reached by ordinary reflection; {@code ConsistentMap} is a
 * public class still missing its {@code serialForm}/{@code serialize} contract and
 * is likewise left to the reflective path until it is brought up to spec.
 *
 * @see MarshalDelegate
 */
public final class LookupUtilMarshalDelegate implements MarshalDelegate {

    /** Public no-arg constructor required by the service-provider discovery. */
    public LookupUtilMarshalDelegate() { }

    private static final Class<?>[] SERVED = { ConsistentMapEntry.class };

    @Override
    public Class<?>[] servedClasses() {
        return SERVED.clone();
    }

    @Override
    public SerialForm[] serialForm(Class<?> c) {
        if (c == ConsistentMapEntry.class) return ConsistentMapEntry.serialForm();
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void serialize(Class<?> c, PutArg arg, Object o) throws IOException {
        if (c == ConsistentMapEntry.class) { ConsistentMapEntry.serialize(arg, (ConsistentMapEntry) o); return; }
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object create(Class<?> c, GetArg arg) throws IOException, ClassNotFoundException {
        if (c == ConsistentMapEntry.class) return new ConsistentMapEntry(arg);
        throw new IllegalArgumentException(unhandled(c));
    }

    private static String unhandled(Class<?> c) {
        return "LookupUtilMarshalDelegate does not serve " + c.getName()
                + "; it serves only org.apache.river.lookup.util.ConsistentMapEntry";
    }
}
