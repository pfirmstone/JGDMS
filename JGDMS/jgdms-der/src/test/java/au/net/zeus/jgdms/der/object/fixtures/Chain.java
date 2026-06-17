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

package au.net.zeus.jgdms.der.object.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;

/**
 * Self-nesting {@code @AtomicSerial} fixture: a {@code Chain} has one field
 * {@code next} of its OWN type (so {@code toWireType} maps it to the
 * {@code "@AtomicSerial"} nested marker). Used to exercise the nested-decode
 * depth (DoS) guard with arbitrarily deep nesting (STD-008 sec.16).
 */
@AtomicSerial
public final class Chain {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("next", Chain.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): the self-nested link. */
    public static void serialize(AtomicSerial.PutArg arg, Chain o) throws IOException {
        arg.put("next", o.next);
        arg.writeArgs();
    }

    private final Chain next;

    public Chain(Chain next) {
        this.next = next;
    }

    public Chain(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.next = (Chain) arg.get("next", null);
    }

    public Chain getNext() {
        return next;
    }

    /** Number of links including this one. */
    public int length() {
        int n = 1;
        for (Chain c = next; c != null; c = c.getNext()) n++;
        return n;
    }
}
