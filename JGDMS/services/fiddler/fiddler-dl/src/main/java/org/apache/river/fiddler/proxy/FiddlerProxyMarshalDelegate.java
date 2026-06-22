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

package org.apache.river.fiddler.proxy;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.MarshalDelegate;
import org.apache.river.fiddler.proxy.FiddlerAdminProxy.ConstrainableFiddlerAdminProxy;
import org.apache.river.fiddler.proxy.FiddlerLease.ConstrainableFiddlerLease;
import org.apache.river.fiddler.proxy.FiddlerProxy.ConstrainableFiddlerProxy;
import org.apache.river.fiddler.proxy.FiddlerRegistration.ConstrainableFiddlerRegistration;

/**
 * {@link MarshalDelegate} for {@code org.apache.river.fiddler.proxy}.
 *
 * <p>Every {@code @AtomicSerial} class in this package is package-private at the
 * member level (its {@code (GetArg)} constructor is package-private, and the
 * {@code Constrainable*} variants are package-private nested classes), so the
 * marshalling engines in {@code org.apache.river.api.io} / {@code jgdms-der}
 * could only reach them by reflection with {@code setAccessible}.  This delegate
 * dispatches each of the three contract members <em>in package</em> — a direct
 * call to the class's own {@code serialForm()} / {@code serialize(PutArg, T)} /
 * {@code (GetArg)} constructor — so there is no reflection and no field access.
 *
 * <p>Dispatch is by exact concrete {@link Class}: {@code serialForm} and
 * {@code serialize} are invoked per class level of the hierarchy; {@code create}
 * is invoked once for the concrete leaf, whose constructor chains up via
 * {@code super(check(arg))}.  Levels in other packages (e.g. the
 * {@code AbstractLease} super-level of {@link FiddlerLease}) are resolved to
 * their own package's delegate or to the reflective fallback, never here.
 *
 * <p>Registered for discovery in
 * {@code META-INF/services/org.apache.river.api.io.MarshalDelegate}.  Requires a
 * public no-argument constructor.
 *
 * @see MarshalDelegate
 */
public final class FiddlerProxyMarshalDelegate implements MarshalDelegate {

    /** Public no-arg constructor required by the service-provider discovery. */
    public FiddlerProxyMarshalDelegate() { }

    @Override
    public SerialForm[] serialForm(Class<?> c) {
        if (c == FiddlerProxy.class)                     return FiddlerProxy.serialForm();
        if (c == ConstrainableFiddlerProxy.class)        return ConstrainableFiddlerProxy.serialForm();
        if (c == FiddlerAdminProxy.class)                return FiddlerAdminProxy.serialForm();
        if (c == ConstrainableFiddlerAdminProxy.class)   return ConstrainableFiddlerAdminProxy.serialForm();
        if (c == FiddlerRegistration.class)              return FiddlerRegistration.serialForm();
        if (c == ConstrainableFiddlerRegistration.class) return ConstrainableFiddlerRegistration.serialForm();
        if (c == FiddlerLease.class)                     return FiddlerLease.serialForm();
        if (c == ConstrainableFiddlerLease.class)        return ConstrainableFiddlerLease.serialForm();
        if (c == FiddlerRenewResults.class)              return FiddlerRenewResults.serialForm();
        if (c == ProxyVerifier.class)                    return ProxyVerifier.serialForm();
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public void serialize(Class<?> c, PutArg arg, Object o) throws IOException {
        if (c == FiddlerProxy.class)                     { FiddlerProxy.serialize(arg, (FiddlerProxy) o); return; }
        if (c == ConstrainableFiddlerProxy.class)        { ConstrainableFiddlerProxy.serialize(arg, (ConstrainableFiddlerProxy) o); return; }
        if (c == FiddlerAdminProxy.class)                { FiddlerAdminProxy.serialize(arg, (FiddlerAdminProxy) o); return; }
        if (c == ConstrainableFiddlerAdminProxy.class)   { ConstrainableFiddlerAdminProxy.serialize(arg, (ConstrainableFiddlerAdminProxy) o); return; }
        if (c == FiddlerRegistration.class)              { FiddlerRegistration.serialize(arg, (FiddlerRegistration) o); return; }
        if (c == ConstrainableFiddlerRegistration.class) { ConstrainableFiddlerRegistration.serialize(arg, (ConstrainableFiddlerRegistration) o); return; }
        if (c == FiddlerLease.class)                     { FiddlerLease.serialize(arg, (FiddlerLease) o); return; }
        if (c == ConstrainableFiddlerLease.class)        { ConstrainableFiddlerLease.serialize(arg, (ConstrainableFiddlerLease) o); return; }
        if (c == FiddlerRenewResults.class)              { FiddlerRenewResults.serialize(arg, (FiddlerRenewResults) o); return; }
        if (c == ProxyVerifier.class)                    { ProxyVerifier.serialize(arg, (ProxyVerifier) o); return; }
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public Object create(Class<?> c, GetArg arg) throws IOException, ClassNotFoundException {
        if (c == FiddlerProxy.class)                     return new FiddlerProxy(arg);
        if (c == ConstrainableFiddlerProxy.class)        return new ConstrainableFiddlerProxy(arg);
        if (c == FiddlerAdminProxy.class)                return new FiddlerAdminProxy(arg);
        if (c == ConstrainableFiddlerAdminProxy.class)   return new ConstrainableFiddlerAdminProxy(arg);
        if (c == FiddlerRegistration.class)              return new FiddlerRegistration(arg);
        if (c == ConstrainableFiddlerRegistration.class) return new ConstrainableFiddlerRegistration(arg);
        if (c == FiddlerLease.class)                     return new FiddlerLease(arg);
        if (c == ConstrainableFiddlerLease.class)        return new ConstrainableFiddlerLease(arg);
        if (c == FiddlerRenewResults.class)              return new FiddlerRenewResults(arg);
        if (c == ProxyVerifier.class)                    return new ProxyVerifier(arg);
        throw new IllegalArgumentException(unhandled(c));
    }

    private static String unhandled(Class<?> c) {
        return "FiddlerProxyMarshalDelegate does not serve " + c.getName()
                + "; it serves only the @AtomicSerial classes of "
                + "org.apache.river.fiddler.proxy";
    }
}
