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

import net.jini.core.entry.Entry;

/**
 * The demo7 headline entry type — a JavaSpace {@link Entry} with two value fields.
 *
 * <p><b>This class is compiled into the CLIENT process only.</b> The Outrigger space
 * (the "server" process) is started with a classpath that deliberately <em>lacks</em>
 * this class; the server proves the absence by a {@code Class.forName(...)} that must
 * throw {@link ClassNotFoundException}. The whole point of demo7 is that the server can
 * evaluate a value predicate — {@code temperatureCelsius > 20.0 &&
 * stationName.startsWith("North")} — against each candidate <em>class-free</em>, over
 * the candidate's own DER (v2) schema, without ever loading {@code WeatherReading}.
 *
 * <p>Fields are public and of reference types (not primitives), as JavaSpace entry
 * fields must be. {@code temperatureCelsius} is a {@link Double} value the positional
 * byte-equality template match cannot express a {@code > 20.0} test over — only the CEL
 * predicate can.
 */
public class WeatherReading implements Entry {

    /** Temperature in degrees Celsius. */
    public Double temperatureCelsius;

    /** Weather station name, e.g. {@code "North Ridge"}. */
    public String stationName;

    /** Required public no-arg constructor for a JavaSpace entry. */
    public WeatherReading() {
    }

    public WeatherReading(Double temperatureCelsius, String stationName) {
        this.temperatureCelsius = temperatureCelsius;
        this.stationName = stationName;
    }

    @Override
    public String toString() {
        return "WeatherReading{temperatureCelsius=" + temperatureCelsius
                + ", stationName=" + stationName + '}';
    }
}
