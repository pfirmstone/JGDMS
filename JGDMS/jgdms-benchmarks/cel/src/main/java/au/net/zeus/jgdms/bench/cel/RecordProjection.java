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

package au.net.zeus.jgdms.bench.cel;

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.eval.CandidateProjection;

import java.util.List;

/**
 * A flyweight {@link CandidateProjection} over "the current record" of three float columns: one namespace
 * ({@link #CLASS_NAME}) declaring the double fields {@code range}, {@code azimuth} and {@code elevation}.
 * <p>
 * {@link #set(int)} is the per-record cost the projection adds: it widens the three stored floats to {@code double}
 * (exactly, as STD-011 §4.2 widening is) and allocates the three {@link CelValue.DoubleV}s ONCE, so that
 * {@link #fieldValue} returns the identical instance on every call within the record, as the
 * {@code CandidateProjection} contract requires (reference stability).
 * <p>
 * DEFICIENCY (booked, not built): no production JGDMS-side rawscan projection exists. A real Service over scan records
 * will need one that reads the processing file through its owner's API; this class is a benchmark fixture only and
 * decodes nothing.
 * <p>
 * Not thread-safe: one instance per evaluating thread.
 */
public final class RecordProjection implements CandidateProjection {

    /** The one namespace of the projection; the name is arbitrary for the benchmark and unrelated to any real schema. */
    public static final String CLASS_NAME = "au.net.zeus.jgdms.bench.cel.RawscanRecord";

    private static final List<String> CHAIN = List.of(CLASS_NAME);

    private final float[] range;
    private final float[] azimuth;
    private final float[] elevation;

    private CelValue.DoubleV rangeValue;
    private CelValue.DoubleV azimuthValue;
    private CelValue.DoubleV elevationValue;

    public RecordProjection(float[] range, float[] azimuth, float[] elevation) {
        this.range = range;
        this.azimuth = azimuth;
        this.elevation = elevation;
        set(0);
    }

    /** Points the flyweight at record {@code i}. */
    public void set(int i) {
        rangeValue = new CelValue.DoubleV(range[i]);
        azimuthValue = new CelValue.DoubleV(azimuth[i]);
        elevationValue = new CelValue.DoubleV(elevation[i]);
    }

    @Override
    public boolean isUndecodable() {
        return false;
    }

    @Override
    public List<String> namespaceChain() {
        return CHAIN;
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        return CLASS_NAME.equals(className)
                && ("range".equals(fieldName) || "azimuth".equals(fieldName) || "elevation".equals(fieldName));
    }

    @Override
    public CelValue fieldValue(String className, String fieldName) {
        return switch (fieldName) {
            case "range" -> rangeValue;
            case "azimuth" -> azimuthValue;
            case "elevation" -> elevationValue;
            default -> throw new IllegalArgumentException(
                    new StringBuilder("undeclared field ").append(className).append('.').append(fieldName).toString());
        };
    }
}
