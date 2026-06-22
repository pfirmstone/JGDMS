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

package org.apache.river.mercury.proxy;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.MarshalDelegate;
import org.apache.river.mercury.proxy.ListenerProxy.ConstrainableListenerProxy;
import org.apache.river.mercury.proxy.MailboxAdminProxy.ConstrainableMailboxAdminProxy;
import org.apache.river.mercury.proxy.MailboxProxy.ConstrainableMailboxProxy;
import org.apache.river.mercury.proxy.Registration.ConstrainableRegistration;

/**
 * {@link MarshalDelegate} for {@code org.apache.river.mercury.proxy}.
 *
 * <p>Dispatches the three {@code @AtomicSerial} contract members in-package, so
 * the marshalling engines never reflect into this package's package-private
 * classes, constructors, or members.  Most classes declare wire state and have
 * all three members.  {@code ConstrainableMailboxAdminProxy} and
 * {@code ConstrainableMailboxProxy} are {@code @AtomicSerial.Stateless}: they
 * declare no wire fields, so {@code serialForm} returns an empty array and
 * {@code serialize} writes nothing, but they are still constructed on read
 * through their {@code (GetArg)} constructor.
 *
 * @see MarshalDelegate
 */
public final class MercuryProxyMarshalDelegate implements MarshalDelegate {

    private static final SerialForm[] EMPTY = new SerialForm[0];

    /** Public no-arg constructor required by the service-provider discovery. */
    public MercuryProxyMarshalDelegate() { }

    @Override
    public SerialForm[] serialForm(Class<?> c) {
        if (c == ListenerProxy.class)               return ListenerProxy.serialForm();
        if (c == ConstrainableListenerProxy.class)  return ConstrainableListenerProxy.serialForm();
        if (c == MailboxAdminProxy.class)           return MailboxAdminProxy.serialForm();
        if (c == MailboxProxy.class)                return MailboxProxy.serialForm();
        if (c == ProxyVerifier.class)               return ProxyVerifier.serialForm();
        if (c == Registration.class)                return Registration.serialForm();
        if (c == ConstrainableRegistration.class)   return ConstrainableRegistration.serialForm();
        if (c == RemoteEventDataCursor.class)       return RemoteEventDataCursor.serialForm();
        if (c == RemoteEventData.class)             return RemoteEventData.serialForm();
        if (c == RemoteEventIteratorData.class)     return RemoteEventIteratorData.serialForm();
        // @Stateless levels: no wire fields.
        if (c == ConstrainableMailboxAdminProxy.class || c == ConstrainableMailboxProxy.class) return EMPTY;
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public void serialize(Class<?> c, PutArg arg, Object o) throws IOException {
        if (c == ListenerProxy.class)               { ListenerProxy.serialize(arg, (ListenerProxy) o); return; }
        if (c == ConstrainableListenerProxy.class)  { ConstrainableListenerProxy.serialize(arg, (ConstrainableListenerProxy) o); return; }
        if (c == MailboxAdminProxy.class)           { MailboxAdminProxy.serialize(arg, (MailboxAdminProxy) o); return; }
        if (c == MailboxProxy.class)                { MailboxProxy.serialize(arg, (MailboxProxy) o); return; }
        if (c == ProxyVerifier.class)               { ProxyVerifier.serialize(arg, (ProxyVerifier) o); return; }
        if (c == Registration.class)                { Registration.serialize(arg, (Registration) o); return; }
        if (c == ConstrainableRegistration.class)   { ConstrainableRegistration.serialize(arg, (ConstrainableRegistration) o); return; }
        if (c == RemoteEventDataCursor.class)       { RemoteEventDataCursor.serialize(arg, (RemoteEventDataCursor) o); return; }
        if (c == RemoteEventData.class)             { RemoteEventData.serialize(arg, (RemoteEventData) o); return; }
        if (c == RemoteEventIteratorData.class)     { RemoteEventIteratorData.serialize(arg, (RemoteEventIteratorData) o); return; }
        // @Stateless levels write nothing.
        if (c == ConstrainableMailboxAdminProxy.class || c == ConstrainableMailboxProxy.class) return;
        throw new IllegalArgumentException(unhandled(c));
    }

    @Override
    public Object create(Class<?> c, GetArg arg) throws IOException, ClassNotFoundException {
        if (c == ListenerProxy.class)               return new ListenerProxy(arg);
        if (c == ConstrainableListenerProxy.class)  return new ConstrainableListenerProxy(arg);
        if (c == MailboxAdminProxy.class)           return new MailboxAdminProxy(arg);
        if (c == ConstrainableMailboxAdminProxy.class) return new ConstrainableMailboxAdminProxy(arg);
        if (c == MailboxProxy.class)                return new MailboxProxy(arg);
        if (c == ConstrainableMailboxProxy.class)   return new ConstrainableMailboxProxy(arg);
        if (c == ProxyVerifier.class)               return new ProxyVerifier(arg);
        if (c == Registration.class)                return new Registration(arg);
        if (c == ConstrainableRegistration.class)   return new ConstrainableRegistration(arg);
        if (c == RemoteEventDataCursor.class)       return new RemoteEventDataCursor(arg);
        if (c == RemoteEventData.class)             return new RemoteEventData(arg);
        if (c == RemoteEventIteratorData.class)     return new RemoteEventIteratorData(arg);
        throw new IllegalArgumentException(unhandled(c));
    }

    private static String unhandled(Class<?> c) {
        return "MercuryProxyMarshalDelegate does not serve " + c.getName()
                + "; it serves only the @AtomicSerial classes of org.apache.river.mercury.proxy";
    }
}
