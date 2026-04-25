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
package org.apache.river.api.codebase;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.net.Uri;

/**
 * An immutable, serializable record submitted by a Phoenix crash reporter
 * to a {@link VerdictRegistry} when an activation group exits abnormally.
 *
 * <p>A Phoenix crash reporter <em>never</em> parses bytecode; its information
 * is already authoritative — the codebase caused a JVM to exit — and therefore
 * does not require further analysis by a {@link BytecodeAnalysisEngine}.  The
 * reporter submits the {@code CrashReport} directly to the registry, which
 * counts it as an implicit {@link VerdictType#DANGEROUS} vote for the
 * affected codebase.
 *
 * <p>A {@code CrashReport} carries:
 * <ul>
 *   <li>the ordered set of codebase URLs that were loaded in the crashed
 *       group,</li>
 *   <li>the OS-level exit code of the group JVM process,</li>
 *   <li>the incarnation number at the time of the crash,</li>
 *   <li>a sanitised (printable-ASCII, length-bounded) excerpt from the
 *       standard-error stream of the crashed process,</li>
 *   <li>the DER-encoded digital signature produced with the Phoenix
 *       activation system's private identity key, authenticating all of
 *       the above fields.</li>
 * </ul>
 *
 * <p><strong>Sanitisation.</strong> The {@code stderrSummary} field is
 * intentionally restricted to printable ASCII and a bounded length so that a
 * malicious process cannot inject control characters, excessively large
 * payloads, or non-printable bytes into the registry's persistent state.
 *
 * <p><strong>Serialization safety.</strong> All fields are validated atomically
 * before the object is constructed, using the {@link AtomicSerial} protocol.
 * The class is {@code final}.
 *
 * @see VerdictRegistry
 * @see BytecodeAnalysisEngine
 * @since 3.1.1
 */
@AtomicSerial
public final class CrashReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Maximum number of characters accepted in {@link #stderrSummary}. */
    public static final int MAX_STDERR_LENGTH = 4096;

    private static final String CODEBASE_URLS  = "codebaseUrls";
    private static final String EXIT_CODE      = "exitCode";
    private static final String INCARNATION    = "incarnation";
    private static final String STDERR_SUMMARY = "stderrSummary";
    private static final String SIGNATURE      = "signature";

    @SuppressWarnings("unused")
    private static final ObjectStreamField[] serialPersistentFields = serialForm();

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(CODEBASE_URLS,  String[].class),
            new SerialForm(EXIT_CODE,      Integer.TYPE),
            new SerialForm(INCARNATION,    Long.TYPE),
            new SerialForm(STDERR_SUMMARY, String.class),
            new SerialForm(SIGNATURE,      byte[].class)
        };
    }

    public static void serialize(PutArg arg, CrashReport cr) throws IOException {
        arg.put(CODEBASE_URLS,  cr.codebaseUrls.clone());
        arg.put(EXIT_CODE,      cr.exitCode);
        arg.put(INCARNATION,    cr.incarnation);
        arg.put(STDERR_SUMMARY, cr.stderrSummary);
        arg.put(SIGNATURE,      cr.signature.clone());
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Invariant check called before construction
    // -------------------------------------------------------------------------

    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
        String[] urls = (String[]) arg.get(CODEBASE_URLS, null);
        if (urls == null || urls.length == 0)
            throw new InvalidObjectException("codebaseUrls must not be null or empty");
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] == null)
                throw new InvalidObjectException("codebaseUrls[" + i + "] must not be null");
            try {
                new Uri(urls[i]);
            } catch (URISyntaxException e) {
                throw new InvalidObjectException(
                        "codebaseUrls[" + i + "] is not a valid RFC3986 URI: " + e.getMessage());
            }
        }
        long incarnation = arg.get(INCARNATION, 0L);
        if (incarnation < 0)
            throw new InvalidObjectException("incarnation must be non-negative");
        String stderr = arg.get(STDERR_SUMMARY, null, String.class);
        if (stderr == null)
            throw new InvalidObjectException("stderrSummary must not be null");
        if (stderr.length() > MAX_STDERR_LENGTH)
            throw new InvalidObjectException(
                    "stderrSummary exceeds " + MAX_STDERR_LENGTH + " characters");
        try {
            checkPrintableAscii(stderr);
        } catch (IllegalArgumentException e) {
            throw new InvalidObjectException(e.getMessage());
        }
        byte[] sig = (byte[]) arg.get(SIGNATURE, null);
        if (sig == null || sig.length == 0)
            throw new InvalidObjectException("signature must not be null or empty");
        return true;
    }

    /**
     * The ordered set of codebase URLs that were active in the crashed
     * activation group, stored as RFC3986 URI strings for safe serialization.
     *
     * @serial
     */
    private final String[] codebaseUrls;

    /**
     * Runtime URL representation of {@link #codebaseUrls}, populated by the
     * constructors.  Not serialized.
     */
    private final transient URL[] codebaseUrlCache;

    /**
     * OS exit code returned by the crashed group JVM process.
     *
     * @serial
     */
    private final int exitCode;

    /**
     * Phoenix incarnation number at the time of the crash.
     *
     * @serial
     */
    private final long incarnation;

    /**
     * Sanitised, length-bounded excerpt from the standard-error output of
     * the crashed process.  Contains only printable ASCII characters
     * (code points 0x20–0x7E, plus {@code '\n'} and {@code '\r'}).
     *
     * @serial
     */
    private final String stderrSummary;

    /**
     * DER-encoded signature produced by Phoenix's identity key over the
     * canonical serialized form of {@link #codebaseUrls}, {@link #exitCode},
     * {@link #incarnation}, and {@link #stderrSummary}.
     *
     * @serial
     */
    private final byte[] signature;

    /**
     * {@link AtomicSerial} deserialization constructor.  Invariants are
     * checked by {@link #check(GetArg)} before any field is assigned.
     */
    public CrashReport(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg, check(arg));
    }

    private CrashReport(GetArg arg, boolean checked) throws IOException, ClassNotFoundException {
        codebaseUrls     = ((String[]) arg.get(CODEBASE_URLS, null)).clone();
        codebaseUrlCache = stringsToUrls(codebaseUrls);
        exitCode      = arg.get(EXIT_CODE, 0);
        incarnation   = arg.get(INCARNATION, 0L);
        stderrSummary = arg.get(STDERR_SUMMARY, null, String.class);
        signature     = ((byte[]) arg.get(SIGNATURE, null)).clone();
    }

    /**
     * Constructs a new {@code CrashReport}.
     *
     * @param codebaseUrls  the ordered set of codebase URLs active in the
     *                      crashed group; must be non-null and non-empty
     * @param exitCode      OS exit code of the crashed JVM process
     * @param incarnation   Phoenix incarnation number at crash time; must be
     *                      non-negative
     * @param stderrSummary sanitised stderr excerpt; must be non-null,
     *                      contain only printable ASCII (plus {@code '\n'}
     *                      and {@code '\r'}), and be at most
     *                      {@value #MAX_STDERR_LENGTH} characters long
     * @param signature     DER-encoded Phoenix identity signature; must be
     *                      non-null and non-empty
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     */
    public CrashReport(URL[] codebaseUrls,
                        int exitCode,
                        long incarnation,
                        String stderrSummary,
                        byte[] signature) {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.length == 0) throw new IllegalArgumentException("codebaseUrls must not be empty");
        for (int i = 0; i < codebaseUrls.length; i++) {
            if (codebaseUrls[i] == null)
                throw new NullPointerException("codebaseUrls[" + i + "]");
        }
        if (incarnation < 0) throw new IllegalArgumentException("incarnation must be non-negative");
        if (stderrSummary == null) throw new NullPointerException("stderrSummary");
        if (stderrSummary.length() > MAX_STDERR_LENGTH)
            throw new IllegalArgumentException(
                    "stderrSummary exceeds " + MAX_STDERR_LENGTH + " characters");
        checkPrintableAscii(stderrSummary);
        if (signature == null) throw new NullPointerException("signature");
        if (signature.length == 0) throw new IllegalArgumentException("signature must not be empty");

        this.codebaseUrls     = urlsToStrings(codebaseUrls);
        this.codebaseUrlCache = codebaseUrls.clone();
        this.exitCode         = exitCode;
        this.incarnation      = incarnation;
        this.stderrSummary    = stderrSummary;
        this.signature        = signature.clone();
    }

    /**
     * Returns an unmodifiable view of the codebase URLs that were active in
     * the crashed group.
     *
     * @return an ordered, unmodifiable set of codebase URLs
     */
    public Set<URL> getCodebaseUrls() {
        Set<URL> result = new LinkedHashSet<URL>(codebaseUrlCache.length * 2);
        for (URL url : codebaseUrlCache) {
            result.add(url);
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the OS exit code returned by the crashed group JVM process.
     *
     * @return the exit code
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Returns the Phoenix incarnation number at the time of the crash.
     *
     * @return a non-negative long
     */
    public long getIncarnation() {
        return incarnation;
    }

    /**
     * Returns the sanitised stderr excerpt from the crashed JVM process.
     *
     * @return a non-null, printable-ASCII string of at most
     *         {@value #MAX_STDERR_LENGTH} characters
     */
    public String getStderrSummary() {
        return stderrSummary;
    }

    /**
     * Returns a copy of the DER-encoded Phoenix identity signature.
     *
     * @return a non-empty byte array; never {@code null}
     */
    public byte[] getSignature() {
        return signature.clone();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that every character in {@code s} is printable ASCII
     * (0x20–0x7E) or a newline / carriage-return.
     */
    private static void checkPrintableAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\n' && c != '\r' && (c < 0x20 || c > 0x7E)) {
                throw new IllegalArgumentException(
                        "stderrSummary contains non-printable-ASCII character 0x"
                                + Integer.toHexString(c) + " at index " + i);
            }
        }
    }

    @Override
    public String toString() {
        return "CrashReport{exitCode=" + exitCode
                + ", incarnation=" + incarnation
                + ", urls=" + Arrays.toString(codebaseUrls) + '}';
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a URL array to RFC3986-normalised String array using {@link Uri}.
     * Throws {@link IllegalArgumentException} if any URL cannot be represented.
     */
    private static String[] urlsToStrings(URL[] urls) {
        String[] result = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            try {
                result[i] = Uri.urlToUri(urls[i]).toString();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(
                        "codebaseUrls[" + i + "] cannot be converted to RFC3986 URI: "
                        + e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * Converts a String array of RFC3986 URIs to a URL array.
     * Called after {@link #check(GetArg)} has already validated the strings.
     */
    private static URL[] stringsToUrls(String[] urls) throws IOException {
        URL[] result = new URL[urls.length];
        for (int i = 0; i < urls.length; i++) {
            try {
                result[i] = new Uri(urls[i]).toURL();
            } catch (URISyntaxException | MalformedURLException e) {
                // Should not happen: check() already validated these strings.
                throw new InvalidObjectException(
                        "codebaseUrls[" + i + "] could not be converted to URL: "
                        + e.getMessage());
            }
        }
        return result;
    }
}
