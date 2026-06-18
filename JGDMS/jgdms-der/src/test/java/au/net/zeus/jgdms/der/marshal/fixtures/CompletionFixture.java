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

package au.net.zeus.jgdms.der.marshal.fixtures;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import net.jini.io.context.DeserializationCompletion;
import org.apache.river.api.io.AtomicSerial;

/**
 * Test fixture that mimics the client-DGC registration done by
 * {@code net.jini.jeri.BasicObjectEndpoint}: its {@code @AtomicSerial} deserialization
 * constructor looks up the {@link DeserializationCompletion} context element exposed by
 * the {@code GetArg} and registers a completion callback on it, to be fired when the
 * decode unit completes (JGDMS-STD-008 sec.6).
 *
 * <p>Used by {@code DerDecodeUnitContextTest} to prove, end-to-end through the real DER
 * codec, that (a) the decode-unit token reaches an {@code @AtomicSerial} constructor via
 * {@code arg.getObjectStreamContext()}, (b) all objects of one decode unit observe the
 * <em>same</em> token (so the DGC dirty batches), and (c) the registered callbacks fire
 * exactly once, on {@code endDecodeUnit}, not during construction.
 */
@AtomicSerial
public final class CompletionFixture {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("id", int.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): emit each serialForm() field by name. */
    public static void serialize(AtomicSerial.PutArg arg, CompletionFixture o) throws IOException {
        arg.put("id", o.id);
        arg.writeArgs();
    }

    private final int id;

    /** Test observation state (not part of the serial form). */
    private transient DeserializationCompletion observedToken;
    private transient int fireCount;

    public CompletionFixture(int id) {
        this.id = id;
    }

    public CompletionFixture(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.id = arg.get("id", 0);
        // Mirror BasicObjectEndpoint's DER path: find the DeserializationCompletion in the
        // object-stream context and register a completion callback on it.
        for (Object next : arg.getObjectStreamContext()) {
            if (next instanceof DeserializationCompletion) {
                this.observedToken = (DeserializationCompletion) next;
                try {
                    this.observedToken.registerCompletion(() -> fireCount++, 0);
                } catch (NotActiveException e) {
                    InvalidObjectException ioe =
                            new InvalidObjectException("decode unit not active");
                    ioe.initCause(e);
                    throw ioe;
                }
                break;
            }
        }
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg) throws IOException {
        return arg;
    }

    public int getId() { return id; }

    /** The decode-unit token observed via {@code getObjectStreamContext()}, or {@code null}. */
    public DeserializationCompletion observedToken() { return observedToken; }

    /** How many times this instance's completion callback has fired. */
    public int fireCount() { return fireCount; }
}
