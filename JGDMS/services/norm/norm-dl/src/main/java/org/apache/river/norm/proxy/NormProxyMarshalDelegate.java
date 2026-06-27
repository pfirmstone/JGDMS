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

package org.apache.river.norm.proxy;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.MarshalDelegate;
import org.apache.river.norm.proxy.AdminProxy.ConstrainableAdminProxy;
import org.apache.river.norm.proxy.NormProxy.ConstrainableNormProxy;
import org.apache.river.norm.proxy.SetProxy.ConstrainableSetProxy;

/**
 * {@link MarshalDelegate} for {@code org.apache.river.norm.proxy}.
 *
 * <p>Dispatches the three {@code @AtomicSerial} contract members in-package, so
 * the marshalling engines never reflect into this package's package-private
 * classes, constructors, or members.  Two shapes are present:
 * <ul>
 *   <li>classes that declare wire state ({@code AbstractProxy} — abstract,
 *       {@code SetProxy}, {@code ConstrainableSetProxy}, {@code GetLeasesResult},
 *       {@code ProxyVerifier}): {@code serialForm}/{@code serialize} forward to the
 *       class's own static members;
 *   <li>{@code @AtomicSerial.Stateless} levels ({@code AdminProxy},
 *       {@code ConstrainableAdminProxy}, {@code NormProxy},
 *       {@code ConstrainableNormProxy}): they declare no wire fields, so
 *       {@code serialForm} returns an empty array and {@code serialize} writes
 *       nothing (the write engine skips them anyway); they are still constructed
 *       on read through their {@code (GetArg)} constructor.
 * </ul>
 * {@code AbstractProxy} is abstract and therefore never a concrete leaf, so it
 * has no {@code create} dispatch.
 *
 * @see MarshalDelegate
 */
public final class NormProxyMarshalDelegate implements MarshalDelegate {

    private static final SerialForm[] EMPTY = new SerialForm[0];

    /** Public no-arg constructor required by the service-provider discovery. */
    public NormProxyMarshalDelegate() { }

    private static final Class<?>[] SERVED = {
        AbstractProxy.class, SetProxy.class, ConstrainableSetProxy.class,
        GetLeasesResult.class, ProxyVerifier.class,
        AdminProxy.class, ConstrainableAdminProxy.class,
        NormProxy.class, ConstrainableNormProxy.class
    };

    @Override
    public Class<?>[] servedClasses() {
        return SERVED.clone();
    }

    @Override
    public SerialForm[] serialForm(Class<?> c) {
        if (c == AbstractProxy.class)         return AbstractProxy.serialForm();
        if (c == SetProxy.class)              return SetProxy.serialForm();
        if (c == ConstrainableSetProxy.class) return ConstrainableSetProxy.serialForm();
        if (c == GetLeasesResult.class)       return GetLeasesResult.serialForm();
        if (c == ProxyVerifier.class)         return ProxyVerifier.serialForm();
        // @Stateless levels: no wire fields.
        if (c == AdminProxy.class || c == ConstrainableAdminProxy.class
                || c == NormProxy.class || c == ConstrainableNormProxy.class) return EMPTY;
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public void serialize(Class<?> c, PutArg arg, Object o) throws IOException {
        if (c == AbstractProxy.class)         { AbstractProxy.serialize(arg, (AbstractProxy) o); return; }
        if (c == SetProxy.class)              { SetProxy.serialize(arg, (SetProxy) o); return; }
        if (c == ConstrainableSetProxy.class) { ConstrainableSetProxy.serialize(arg, (ConstrainableSetProxy) o); return; }
        if (c == GetLeasesResult.class)       { GetLeasesResult.serialize(arg, (GetLeasesResult) o); return; }
        if (c == ProxyVerifier.class)         { ProxyVerifier.serialize(arg, (ProxyVerifier) o); return; }
        // @Stateless levels write nothing.
        if (c == AdminProxy.class || c == ConstrainableAdminProxy.class
                || c == NormProxy.class || c == ConstrainableNormProxy.class) return;
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public Object create(Class<?> c, GetArg arg) throws IOException, ClassNotFoundException {
        // AbstractProxy is abstract -> never a concrete leaf -> no create.
        if (c == SetProxy.class)              return new SetProxy(arg);
        if (c == ConstrainableSetProxy.class) return new ConstrainableSetProxy(arg);
        if (c == GetLeasesResult.class)       return new GetLeasesResult(arg);
        if (c == ProxyVerifier.class)         return new ProxyVerifier(arg);
        if (c == AdminProxy.class)            return new AdminProxy(arg);
        if (c == ConstrainableAdminProxy.class) return new ConstrainableAdminProxy(arg);
        if (c == NormProxy.class)             return new NormProxy(arg);
        if (c == ConstrainableNormProxy.class) return new ConstrainableNormProxy(arg);
        throw new IllegalArgumentException(unhandled(c));
    }

    private static String unhandled(Class<?> c) {
        return "NormProxyMarshalDelegate does not serve " + c.getName()
                + "; it serves only the @AtomicSerial classes of org.apache.river.norm.proxy";
    }
}
