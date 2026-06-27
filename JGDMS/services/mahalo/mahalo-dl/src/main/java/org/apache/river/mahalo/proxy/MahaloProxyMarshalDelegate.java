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

package org.apache.river.mahalo.proxy;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.MarshalDelegate;
import org.apache.river.mahalo.proxy.TxnMgrAdminProxy.ConstrainableTxnMgrAdminProxy;
import org.apache.river.mahalo.proxy.TxnMgrProxy.ConstrainableTxnMgrProxy;

/**
 * {@link MarshalDelegate} for {@code org.apache.river.mahalo.proxy}.
 *
 * <p>Every {@code @AtomicSerial} class here needs in-package dispatch: the public
 * {@code ProxyVerifier}, {@code TxnMgrProxy}, and {@code TxnMgrAdminProxy} have
 * package-private {@code (GetArg)} constructors (so read construction would
 * otherwise need {@code setAccessible}), and the {@code @AtomicSerial.Stateless}
 * leaves {@code ConstrainableTxnMgrProxy} / {@code ConstrainableTxnMgrAdminProxy}
 * are package-private nested classes whose own package-private {@code (GetArg)}
 * constructors must still run on read.  {@code @Stateless} levels contribute no
 * wire fields (empty {@code serialForm}, no-op {@code serialize}).
 *
 * @see MarshalDelegate
 */
public final class MahaloProxyMarshalDelegate implements MarshalDelegate {

    private static final SerialForm[] EMPTY = new SerialForm[0];

    /** Public no-arg constructor required by the service-provider discovery. */
    public MahaloProxyMarshalDelegate() { }

    private static final Class<?>[] SERVED = {
        ProxyVerifier.class,
        TxnMgrAdminProxy.class, ConstrainableTxnMgrAdminProxy.class,
        TxnMgrProxy.class, ConstrainableTxnMgrProxy.class
    };

    @Override
    public Class<?>[] servedClasses() {
        return SERVED.clone();
    }

    @Override
    public SerialForm[] serialForm(Class<?> c) {
        if (c == ProxyVerifier.class)    return ProxyVerifier.serialForm();
        if (c == TxnMgrAdminProxy.class) return TxnMgrAdminProxy.serialForm();
        if (c == TxnMgrProxy.class)      return TxnMgrProxy.serialForm();
        // @Stateless leaves: no wire fields.
        if (c == ConstrainableTxnMgrAdminProxy.class || c == ConstrainableTxnMgrProxy.class) return EMPTY;
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public void serialize(Class<?> c, PutArg arg, Object o) throws IOException {
        if (c == ProxyVerifier.class)    { ProxyVerifier.serialize(arg, (ProxyVerifier) o); return; }
        if (c == TxnMgrAdminProxy.class) { TxnMgrAdminProxy.serialize(arg, (TxnMgrAdminProxy) o); return; }
        if (c == TxnMgrProxy.class)      { TxnMgrProxy.serialize(arg, (TxnMgrProxy) o); return; }
        // @Stateless leaves write nothing.
        if (c == ConstrainableTxnMgrAdminProxy.class || c == ConstrainableTxnMgrProxy.class) return;
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public Object create(Class<?> c, GetArg arg) throws IOException, ClassNotFoundException {
        if (c == ProxyVerifier.class)                 return new ProxyVerifier(arg);
        if (c == TxnMgrAdminProxy.class)              return new TxnMgrAdminProxy(arg);
        if (c == ConstrainableTxnMgrAdminProxy.class) return new ConstrainableTxnMgrAdminProxy(arg);
        if (c == TxnMgrProxy.class)                   return new TxnMgrProxy(arg);
        if (c == ConstrainableTxnMgrProxy.class)      return new ConstrainableTxnMgrProxy(arg);
        throw new IllegalArgumentException(unhandled(c));
    }

    private static String unhandled(Class<?> c) {
        return "MahaloProxyMarshalDelegate does not serve " + c.getName()
                + "; it serves only the @AtomicSerial classes of org.apache.river.mahalo.proxy";
    }
}
