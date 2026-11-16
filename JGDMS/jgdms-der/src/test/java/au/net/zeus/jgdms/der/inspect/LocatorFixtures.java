/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package au.net.zeus.jgdms.der.inspect;

import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code @AtomicSerial} fixtures for {@link EndpointLocatorTest}, shaped like the JERI classes a
 * proxy is made of (an endpoint, an object endpoint holding it in an interface-typed slot, an
 * invocation handler, a carrier holding a handler and a {@code MarshalledInstance}). The locator
 * never constructs them, so they have no {@code GetArg} constructors.
 */
final class LocatorFixtures {

    private LocatorFixtures() {
    }

    /** Stands in for {@code net.jini.jeri.Endpoint}. */
    public interface FxEndpointType {
    }

    /** Stands in for {@code javax.net.SocketFactory}. */
    public interface FxSocketFactory {
    }

    /** A remote interface for proxies. */
    public interface FxService {
        String hello();
    }

    @AtomicSerial
    public static final class FxSocketFactoryImpl implements FxSocketFactory {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {new AtomicSerial.SerialForm("name", String.class)};
        }

        public static void serialize(AtomicSerial.PutArg arg, FxSocketFactoryImpl o) throws IOException {
            arg.put("name", o.name);
            arg.writeArgs();
        }

        final String name;

        public FxSocketFactoryImpl(String name) {
            this.name = name;
        }
    }

    /** The recognised endpoint: host, port, socket factory. */
    @AtomicSerial
    public static final class FxEndpoint implements FxEndpointType {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("host", String.class),
                new AtomicSerial.SerialForm("port", int.class),
                new AtomicSerial.SerialForm("sf", FxSocketFactory.class)
            };
        }

        public static void serialize(AtomicSerial.PutArg arg, FxEndpoint o) throws IOException {
            arg.put("host", o.host);
            arg.put("port", o.port);
            arg.put("sf", o.sf);
            arg.writeArgs();
        }

        final String host;
        final int port;
        final FxSocketFactory sf;

        public FxEndpoint(String host, int port, FxSocketFactory sf) {
            this.host = host;
            this.port = port;
            this.sf = sf;
        }
    }

    /** An endpoint-typed value the locator does not recognise. */
    @AtomicSerial
    public static final class FxUnrecognised implements FxEndpointType {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {new AtomicSerial.SerialForm("note", String.class)};
        }

        public static void serialize(AtomicSerial.PutArg arg, FxUnrecognised o) throws IOException {
            arg.put("note", o.note);
            arg.writeArgs();
        }

        final String note;

        public FxUnrecognised(String note) {
            this.note = note;
        }
    }

    /** Stands in for {@code BasicObjectEndpoint}: {@code ep} is the required slot. */
    @AtomicSerial
    public static final class FxObjectEndpoint {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("ep", FxEndpointType.class),
                new AtomicSerial.SerialForm("id", String.class)
            };
        }

        public static void serialize(AtomicSerial.PutArg arg, FxObjectEndpoint o) throws IOException {
            arg.put("ep", o.ep);
            arg.put("id", o.id);
            arg.writeArgs();
        }

        final FxEndpointType ep;
        final String id;

        public FxObjectEndpoint(FxEndpointType ep, String id) {
            this.ep = ep;
            this.id = id;
        }
    }

    /** Stands in for {@code BasicInvocationHandler}. */
    @AtomicSerial
    public static final class FxHandler implements InvocationHandler {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {new AtomicSerial.SerialForm("oe", FxObjectEndpoint.class)};
        }

        public static void serialize(AtomicSerial.PutArg arg, FxHandler o) throws IOException {
            arg.put("oe", o.oe);
            arg.writeArgs();
        }

        final FxObjectEndpoint oe;

        public FxHandler(FxObjectEndpoint oe) {
            this.oe = oe;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    /** Stands in for {@code DerProxySerializer}: a handler and the smart proxy's MarshalledInstance. */
    @AtomicSerial
    public static final class FxCarrier {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("handler", InvocationHandler.class),
                new AtomicSerial.SerialForm("serviceProxy", MarshalledInstance.class)
            };
        }

        public static void serialize(AtomicSerial.PutArg arg, FxCarrier o) throws IOException {
            arg.put("handler", o.handler);
            arg.put("serviceProxy", o.serviceProxy);
            arg.writeArgs();
        }

        final InvocationHandler handler;
        final MarshalledInstance serviceProxy;

        public FxCarrier(InvocationHandler handler, MarshalledInstance serviceProxy) {
            this.handler = handler;
            this.serviceProxy = serviceProxy;
        }
    }

    /** One of each container kind the locator must descend into. */
    @AtomicSerial
    public static final class FxHolder {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("tag", String.class),
                new AtomicSerial.SerialForm("service", FxService.class),
                new AtomicSerial.SerialForm("direct", FxEndpointType.class),
                new AtomicSerial.SerialForm("array", FxEndpointType[].class),
                new AtomicSerial.SerialForm("list", List.class),
                new AtomicSerial.SerialForm("map", Map.class),
                new AtomicSerial.SerialForm("set", Set.class),
                new AtomicSerial.SerialForm("any", List.class),
                new AtomicSerial.SerialForm("mi", MarshalledInstance.class)
            };
        }

        public static void serialize(AtomicSerial.PutArg arg, FxHolder o) throws IOException {
            arg.put("tag", o.tag);
            arg.put("service", o.service);
            arg.put("direct", o.direct);
            arg.put("array", o.array);
            arg.put("list", o.list);
            arg.put("map", o.map);
            arg.put("set", o.set);
            arg.put("any", o.any);
            arg.put("mi", o.mi);
            arg.writeArgs();
        }

        final String tag;
        FxService service;
        FxEndpointType direct;
        FxEndpointType[] array;
        List<FxEndpointType> list;
        Map<String, FxEndpointType> map;
        Set<FxEndpointType> set;
        List<Object> any;
        MarshalledInstance mi;

        public FxHolder(String tag) {
            this.tag = tag;
        }
    }
}
