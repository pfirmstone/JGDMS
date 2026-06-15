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
import java.util.Objects;

/**
 * B1 inc-3 fixture: an {@code @AtomicSerial} class with a nullable enum field
 * (wireType {@code "enum:<class>"}) and a non-null label field.
 */
@AtomicSerial
public final class EnumRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[]{
            new AtomicSerial.SerialForm("label",  String.class),
            new AtomicSerial.SerialForm("status", Status.class),
        };
    }

    private final String label;
    private final Status status; // nullable

    public EnumRecord(String label, Status status) {
        this.label  = Objects.requireNonNull(label, "label");
        this.status = status;
    }

    public EnumRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.label  = (String) arg.get("label",  null);
        this.status = (Status) arg.get("status", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        if (arg.get("label", null) == null) {
            throw new java.io.InvalidObjectException("EnumRecord: label must not be null");
        }
        return arg;
    }

    public String getLabel()  { return label; }
    public Status getStatus() { return status; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EnumRecord that)) return false;
        return Objects.equals(label, that.label)
                && status == that.status;
    }

    @Override
    public int hashCode() { return Objects.hash(label, status); }

    @Override
    public String toString() {
        return "EnumRecord{label='" + label + "', status=" + status + '}';
    }
}
