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
 * Middle level of the showcase value type: a telemetry record that additionally
 * says what was measured and in what unit. Each level of the family tree carries
 * its own shape description on the wire.
 */
@AtomicSerial
public class SensorReading extends TelemetryRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("measuredQuantityName", String.class),
            new AtomicSerial.SerialForm("unitOfMeasurement",    String.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, SensorReading o) throws IOException {
        arg.put("measuredQuantityName", o.measuredQuantityName);
        arg.put("unitOfMeasurement",    o.unitOfMeasurement);
        arg.writeArgs();
    }

    private final String measuredQuantityName;
    private final String unitOfMeasurement;

    public SensorReading(long sequenceNumber, String originatingStationName,
                         String measuredQuantityName, String unitOfMeasurement) {
        super(sequenceNumber, originatingStationName);
        this.measuredQuantityName = Objects.requireNonNull(measuredQuantityName, "measuredQuantityName");
        this.unitOfMeasurement    = Objects.requireNonNull(unitOfMeasurement, "unitOfMeasurement");
    }

    public SensorReading(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(check(arg));
        this.measuredQuantityName = (String) arg.get("measuredQuantityName", null);
        this.unitOfMeasurement    = (String) arg.get("unitOfMeasurement", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        if (arg.get("measuredQuantityName", null) == null) {
            throw new InvalidObjectException("SensorReading: measuredQuantityName must not be null");
        }
        return arg;
    }

    public String getMeasuredQuantityName() { return measuredQuantityName; }
    public String getUnitOfMeasurement()    { return unitOfMeasurement; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SensorReading that)) return false;
        return Objects.equals(measuredQuantityName, that.measuredQuantityName)
                && Objects.equals(unitOfMeasurement, that.unitOfMeasurement)
                && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), measuredQuantityName, unitOfMeasurement);
    }

    @Override
    public String toString() {
        return "SensorReading{" + super.toString()
                + ", measuredQuantityName='" + measuredQuantityName + "'"
                + ", unitOfMeasurement='" + unitOfMeasurement + "'}";
    }
}
