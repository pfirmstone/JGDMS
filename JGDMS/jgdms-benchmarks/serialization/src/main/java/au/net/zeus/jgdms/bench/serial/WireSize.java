/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

/**
 * Prints the encoded size of each payload in each format: the bytes one
 * fresh stream carrying one object occupies on the wire, which is what one
 * JERI request or response carries for that payload.
 *
 * <p>Not a timing benchmark; run it once alongside the JMH results:
 * {@code java -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.WireSize}
 */
public final class WireSize {

    private WireSize() {}

    public static void main(String[] args) throws Exception {
        ClassLoader loader = WireSize.class.getClassLoader();
        System.out.printf("%-10s %10s %10s %8s%n", "payload", "JOSS", "DER", "DER/JOSS");
        for (String name : Payloads.NAMES) {
            Object o = Payloads.create(name);
            int joss = Format.JOSS.encode(o, loader).length;
            int der = Format.DER.encode(o, loader).length;
            System.out.printf("%-10s %10d %10d %8.2f%n", name, joss, der, (double) der / joss);
        }
    }
}
