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

package au.net.zeus.jgdms.showcase.rule.records;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;

/**
 * A weather reading a field station puts on the wire. This is the STATION's own
 * class. In the demonstration it is deliberately compiled into a separate folder so
 * it can be placed on the producer's classpath and left OFF the reader's classpath —
 * proving the reader filters these readings by a written rule without ever having the
 * class.
 *
 * <p>The reader never sees this source or this compiled class. It only ever sees the
 * on-the-wire bytes, plus the small shape description ("schema") that travels with them.
 */
@AtomicSerial
public final class WeatherReading {

    /**
     * The wire-ordered shape. Each entry pairs a field name with its type; this is
     * exactly the description that travels in the message so a reader can name the
     * fields without the class.
     */
    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("stationName",        String.class),
            new AtomicSerial.SerialForm("temperatureCelsius", double.class),
            new AtomicSerial.SerialForm("humidityPercent",    double.class),
            new AtomicSerial.SerialForm("sequenceNumber",     int.class),
        };
    }

    /** The write contract: emit each field by name. The codec never reflects on private fields. */
    public static void serialize(AtomicSerial.PutArg arg, WeatherReading o) throws IOException {
        arg.put("stationName",        o.stationName);
        arg.put("temperatureCelsius", o.temperatureCelsius);
        arg.put("humidityPercent",    o.humidityPercent);
        arg.put("sequenceNumber",     o.sequenceNumber);
        arg.writeArgs();
    }

    private final String stationName;
    private final double temperatureCelsius;
    private final double humidityPercent;
    private final int    sequenceNumber;

    public WeatherReading(String stationName, double temperatureCelsius,
                          double humidityPercent, int sequenceNumber) {
        this.stationName        = stationName;
        this.temperatureCelsius = temperatureCelsius;
        this.humidityPercent    = humidityPercent;
        this.sequenceNumber     = sequenceNumber;
    }

    /** Reconstruction constructor — needed only by a reader that HAS the class (the reader here does not). */
    public WeatherReading(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.stationName        = (String) arg.get("stationName", null);
        this.temperatureCelsius = arg.get("temperatureCelsius", 0.0);
        this.humidityPercent    = arg.get("humidityPercent", 0.0);
        this.sequenceNumber     = arg.get("sequenceNumber", 0);
    }

    @Override
    public String toString() {
        return "WeatherReading{stationName='" + stationName
                + "', temperatureCelsius=" + temperatureCelsius
                + ", humidityPercent=" + humidityPercent
                + ", sequenceNumber=" + sequenceNumber + '}';
    }
}
