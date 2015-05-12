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
package org.apache.river.test.impl.outrigger.matching;


/**
 * Utility class which collects timing statistics.
 */
class TimingStatistics {

    /**
     * Running average of data points.
     */
    private double average;

    /**
     * Running minimum of data points.
     */
    private long min;

    /**
     * Running maximum of data points.
     */
    private long max;

    /**
     * Running count of data points.
     */
    private long count;

    /**
     * Constructor.
     */
    TimingStatistics() {

        // Do nothing.
    }

    /**
     * Clears the internal state of this object.
     */
    void reset() {
        average = 0.0;
        min = max = 0;
        count = 0;
    }

    /**
     * Computes running average, minimum, and maximum values.
     */
    void computeStats(long before, long after) {
        if (after < before) {
            throw new IllegalArgumentException("After time < Before time");
        }
        long delta = after - before;

        if (count == 0) {
            average = (double) delta;
            min = max = delta;
        } else {
            average = ((average * count) + delta) / (count + 1);

            if (delta < min) {
                min = delta;
            }

            if (delta > max) {
                max = delta;
            }
        }
        ++count;
    }

    /**
     * Prints the current vaules for average, minimum, maximum, and count.
     */
    String getStats() {
        return "Avg=" + average + ", Min=" + min + ", Max=" + max + ", Count="
                + count;
    }

    /**
     * Unit test driver method.
     */
    public static void main(String[] args) {
        int i = 0;
        TimingStatistics ts = new TimingStatistics();
        System.out.println(ts.getStats());
        ts.reset();
        long[] known = { 0 };

        for (i = 0; i < known.length; i++) {
            ts.computeStats(known[0], known[i]);
        }
        System.out.println(ts.getStats());
        ts.reset();
        known = new long[] {
            0, 1, 2, 3, 4, 5 };

        for (i = 0; i < known.length; i++) {
            ts.computeStats(known[0], known[i]);
        }
        System.out.println(ts.getStats());
        ts.reset();
        known = new long[] {
            1, 2, 0, 4, 5 };

        for (i = 0; i < known.length; i++) {
            ts.computeStats(known[0], known[i]);
        }
        System.out.println(ts.getStats());
    }
}
