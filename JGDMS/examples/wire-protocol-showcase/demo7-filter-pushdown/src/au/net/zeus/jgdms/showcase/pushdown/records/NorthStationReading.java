/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.showcase.pushdown.records;

/**
 * A subclass of {@link WeatherReading} adding one field. Used for the demo7 &sect;8.4
 * "subclass candidate filtered by an inherited field" case: a {@code WeatherReading}
 * template byte-matches this subclass, and the predicate authored over
 * {@code WeatherReading.class} — which references only the <em>inherited</em>
 * {@code temperatureCelsius}/{@code stationName} fields — must evaluate correctly
 * against the subclass candidate's own schema (the filter resolves to its originating
 * schema schema-lessly; a warm-northern {@code NorthStationReading} passes).
 *
 * <p>Like {@link WeatherReading}, this type is compiled into the CLIENT only; the
 * server never loads it.
 */
public class NorthStationReading extends WeatherReading {

    /** Station elevation in metres — the added (non-inherited) field. */
    public Integer elevationMetres;

    /** Required public no-arg constructor for a JavaSpace entry. */
    public NorthStationReading() {
        super();
    }

    public NorthStationReading(Double temperatureCelsius, String stationName,
                               Integer elevationMetres) {
        super(temperatureCelsius, stationName);
        this.elevationMetres = elevationMetres;
    }

    @Override
    public String toString() {
        return "NorthStationReading{temperatureCelsius=" + temperatureCelsius
                + ", stationName=" + stationName
                + ", elevationMetres=" + elevationMetres + '}';
    }
}
