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
 * A single survey observation from an instrument: a target name, a horizontal
 * bearing and vertical angle in degrees, and a slope distance in metres. Like
 * {@link WeatherReading}, this is the instrument's own class, deliberately kept OFF
 * the reader's classpath so the reader must work from the bytes and the travelling
 * shape alone.
 *
 * <p>It is here to give the "same rule, same answer — exactly" section a real transform
 * to run: turning the bearing, vertical angle and distance into a local direction
 * vector (east, north, up), using the correctly-rounded trigonometry the rule language
 * ships with.
 */
@AtomicSerial
public final class SurveyObservation {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("targetName",          String.class),
            new AtomicSerial.SerialForm("bearingDegrees",      double.class),
            new AtomicSerial.SerialForm("elevationDegrees",    double.class),
            new AtomicSerial.SerialForm("slopeDistanceMetres", double.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, SurveyObservation o) throws IOException {
        arg.put("targetName",          o.targetName);
        arg.put("bearingDegrees",      o.bearingDegrees);
        arg.put("elevationDegrees",    o.elevationDegrees);
        arg.put("slopeDistanceMetres", o.slopeDistanceMetres);
        arg.writeArgs();
    }

    private final String targetName;
    private final double bearingDegrees;
    private final double elevationDegrees;
    private final double slopeDistanceMetres;

    public SurveyObservation(String targetName, double bearingDegrees,
                             double elevationDegrees, double slopeDistanceMetres) {
        this.targetName          = targetName;
        this.bearingDegrees      = bearingDegrees;
        this.elevationDegrees    = elevationDegrees;
        this.slopeDistanceMetres = slopeDistanceMetres;
    }

    public SurveyObservation(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.targetName          = (String) arg.get("targetName", null);
        this.bearingDegrees      = arg.get("bearingDegrees", 0.0);
        this.elevationDegrees    = arg.get("elevationDegrees", 0.0);
        this.slopeDistanceMetres = arg.get("slopeDistanceMetres", 0.0);
    }

    @Override
    public String toString() {
        return "SurveyObservation{targetName='" + targetName
                + "', bearingDegrees=" + bearingDegrees
                + ", elevationDegrees=" + elevationDegrees
                + ", slopeDistanceMetres=" + slopeDistanceMetres + '}';
    }
}
