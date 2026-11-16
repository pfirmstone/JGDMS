/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tests.support;

import java.lang.reflect.InvocationTargetException;
import java.security.CodeSource;
import java.security.cert.Certificate;

/**
 * {@code java.security.DigestCodeSource} for tests, by reflection: it is
 * only in DirtyChai, and the versions of it differ.
 */
public final class DigestCodeSources {

    private static final String NAME = "java.security.DigestCodeSource";

    private DigestCodeSources() {}

    /**
     * The DigestCodeSource class, if it is present and has what the tests
     * of digest code sources need, otherwise null: a constructor that
     * computes the digest from the code, one that is given the digest 
     * (which is not trusted) and {@code getDigest()} and {@code unverified()}.
     * A test that needs more than this must check for it, and skip, not fail.
     * 
     * @return the class, or null
     */
    public static Class<?> usable() {
        try {
            Class<?> c = Class.forName(NAME);
            c.getConstructor(String.class, Certificate[].class, String.class);
            c.getConstructor(String.class, Certificate[].class, String.class, byte[].class);
            c.getMethod("getDigest");
            c.getMethod("unverified");
            return c;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Constructs a DigestCodeSource that computes its digest from the code.
     * 
     * @param url the location of the code
     * @param certs the signers, or null
     * @param algorithm the digest algorithm
     * @return the code source
     * @throws SecurityException if the DigestCodeSource refuses the code, 
     * as it does when the code at a location has changed since a digest was
     * first computed for it.
     * @throws Exception for any other failure
     */
    public static CodeSource computed(String url, Certificate[] certs, String algorithm) 
            throws Exception {
        try {
            return (CodeSource) usable().getConstructor(String.class, Certificate[].class, 
                    String.class).newInstance(url, certs, algorithm);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    /**
     * Constructs a DigestCodeSource that is given its digest: it has not been
     * verified.
     * 
     * @param url the location of the code
     * @param certs the signers, or null
     * @param algorithm the digest algorithm
     * @param digest the digest asserted
     * @return the code source
     * @throws Exception for any failure
     */
    public static CodeSource asserted(String url, Certificate[] certs, String algorithm, 
            byte[] digest) throws Exception {
        try {
            return (CodeSource) usable().getConstructor(String.class, Certificate[].class, 
                    String.class, byte[].class).newInstance(url, certs, algorithm, digest);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    /**
     * @param cs a DigestCodeSource
     * @return its digest
     * @throws Exception for any failure
     */
    public static byte[] digestOf(CodeSource cs) throws Exception {
        return (byte[]) usable().getMethod("getDigest").invoke(cs);
    }

    private static Exception unwrap(InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception) return (Exception) cause;
        if (cause instanceof Error) throw (Error) cause;
        return e;
    }
}
