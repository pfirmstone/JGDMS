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
 * Leaf level of the showcase value type: a sensor reading with a calibrated value
 * and a reference to the calibration certificate it was produced under. Writing one
 * of these to the wire carries the shape description for all three levels of the
 * family tree ({@code CalibratedReading}, {@link SensorReading},
 * {@link TelemetryRecord}) — the "family tree description" the showcase measures.
 */
@AtomicSerial
public final class CalibratedReading extends SensorReading {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("calibratedValue",                 double.class),
            new AtomicSerial.SerialForm("calibrationCertificateReference", String.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, CalibratedReading o) throws IOException {
        arg.put("calibratedValue",                 o.calibratedValue);
        arg.put("calibrationCertificateReference", o.calibrationCertificateReference);
        arg.writeArgs();
    }

    private final double calibratedValue;
    private final String calibrationCertificateReference;

    public CalibratedReading(long sequenceNumber, String originatingStationName,
                             String measuredQuantityName, String unitOfMeasurement,
                             double calibratedValue, String calibrationCertificateReference) {
        super(sequenceNumber, originatingStationName, measuredQuantityName, unitOfMeasurement);
        this.calibratedValue                 = calibratedValue;
        this.calibrationCertificateReference =
                Objects.requireNonNull(calibrationCertificateReference, "calibrationCertificateReference");
    }

    public CalibratedReading(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(check(arg));
        this.calibratedValue                 = arg.get("calibratedValue", 0.0d);
        this.calibrationCertificateReference =
                arg.get("calibrationCertificateReference", null, String.class);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        if (arg.get("calibrationCertificateReference", null, String.class) == null) {
            throw new InvalidObjectException(
                    "CalibratedReading: calibrationCertificateReference must not be null");
        }
        return arg;
    }

    public double getCalibratedValue()                 { return calibratedValue; }
    public String getCalibrationCertificateReference() { return calibrationCertificateReference; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CalibratedReading that)) return false;
        return Double.compare(calibratedValue, that.calibratedValue) == 0
                && Objects.equals(calibrationCertificateReference, that.calibrationCertificateReference)
                && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), calibratedValue, calibrationCertificateReference);
    }

    @Override
    public String toString() {
        return "CalibratedReading{" + super.toString()
                + ", calibratedValue=" + calibratedValue
                + ", calibrationCertificateReference='" + calibrationCertificateReference + "'}";
    }
}
