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

package au.net.zeus.jgdms.showcase.model;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Objects;

/**
 * Root of a three-level value type used by the wire-format showcase.
 *
 * <p>The three classes ({@code TelemetryRecord}, {@link SensorReading},
 * {@link CalibratedReading}) each describe their own fields. When one of these
 * objects is written to the wire, the description of its shape (the field names
 * and types, for every level of the family tree) travels alongside the data. The
 * showcase measures what that description costs, and how the wire format sends it
 * once and then refers back to it.
 *
 * <p>The field names here are deliberately descriptive (and therefore not tiny),
 * because that is the honest, realistic case: real business objects have real
 * field names, and the shape description is a meaningful fraction of each record.
 */
@AtomicSerial
public class TelemetryRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("sequenceNumber",          long.class),
            new AtomicSerial.SerialForm("originatingStationName",  String.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, TelemetryRecord o) throws IOException {
        arg.put("sequenceNumber",         o.sequenceNumber);
        arg.put("originatingStationName", o.originatingStationName);
        arg.writeArgs();
    }

    private final long   sequenceNumber;
    private final String originatingStationName;

    public TelemetryRecord(long sequenceNumber, String originatingStationName) {
        this.sequenceNumber         = sequenceNumber;
        this.originatingStationName = Objects.requireNonNull(originatingStationName, "originatingStationName");
    }

    public TelemetryRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.sequenceNumber         = arg.get("sequenceNumber", 0L);
        this.originatingStationName = arg.get("originatingStationName", null, String.class);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        if (arg.get("originatingStationName", null, String.class) == null) {
            throw new InvalidObjectException("TelemetryRecord: originatingStationName must not be null");
        }
        return arg;
    }

    public long   getSequenceNumber()         { return sequenceNumber; }
    public String getOriginatingStationName() { return originatingStationName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TelemetryRecord that)) return false;
        return sequenceNumber == that.sequenceNumber
                && Objects.equals(originatingStationName, that.originatingStationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber, originatingStationName);
    }

    @Override
    public String toString() {
        return "TelemetryRecord{sequenceNumber=" + sequenceNumber
                + ", originatingStationName='" + originatingStationName + "'}";
    }
}
