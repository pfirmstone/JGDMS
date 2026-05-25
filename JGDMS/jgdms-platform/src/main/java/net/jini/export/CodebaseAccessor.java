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

package net.jini.export;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.rmi.Remote;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.RevocablePolicy;

/**
 * After authenticating a bootstrap token proxy, the ProxyPreparer can
 * dynamically grant DownloadPermission and DeSerializationPermission
 * as required using the information provided, to allow downloading 
 * of a smart proxy.
 * 
 * To make a ProtectionDomain or CodeSource based grant requires a 
 * {@link RevocablePolicy#grant(PermissionGrant) }
 * 
 * A service needn't implement this if a proxy doesn't require a codebase 
 * download.
 * 
 * Certificates et al are sent in encoded format.  The choice was made not
 * to Serialize Certificate or CodeSigner in case the CertificateFactory
 * provider isn't installed and to also allow low level {@link java.io.DataInput} and
 * {@link java.io.DataOutput} based communication.
 * 
 * @see RevocablePolicy
 * @see PermissionGrant
 */
public interface CodebaseAccessor extends Remote {
    
    /**
     * Obtains the service class annotation as defined in
     * <code> ClassLoading.getClassAnnotation(Class)</code>.
     * 
     * @return the codebase annotation.
     * @throws IOException if a connection problem occurs.
     */
    public String getClassAnnotation() throws IOException;
    
    /**
     * Get the CertificateFactory type.
     * 
     * @return CertificateFactory type or null.
     * @throws IOException if a connection problem occurs.
     * @see CertificateFactory#getInstance(java.lang.String) 
     */
    public String getCertFactoryType() throws IOException;
    
    /**
     * Get the CertPath encoding;
     * @return CertPath encoding or null.
     * @throws IOException if a connection problem occurs.
     * @see CertPath#CertPath(java.lang.String) 
     */
    public String getCertPathEncoding() throws IOException;
    
    /**
     * The byte array can be passed to a ByteArrayInputStream, which can be
     * passed to a CertificateFactory to generate a Collection of Certificates,
     * or CertPath.
     * 
     * @return a byte array containing certificates or null.
     * @throws IOException if a connection problem occurs.
     * @see ByteArrayInputStream
     * @see CertificateFactory#generateCertPath(java.io.InputStream) 
     * @see CertificateFactory#generateCertificates(java.io.InputStream) 
     */
    public byte [] getEncodedCerts() throws IOException;

    /**
     * Returns the name of the digest algorithm used to compute the codebase
     * digest returned by {@link #getCodebaseDigest()}.
     *
     * <p>The default implementation returns {@code null}, indicating that the
     * service does not supply a codebase digest.  Implementations should
     * return a standard algorithm name such as {@code "SHA-256"}.
     *
     * <p>The digest is transmitted over the already-authenticated SPIFFE/TLS
     * channel and is therefore integrity-protected by the transport layer.
     *
     * @return the digest algorithm name, or {@code null} if not supported
     * @throws IOException if a communication problem occurs
     */
    public default String getCodebaseDigestAlgorithm() throws IOException {
        return null;
    }

    /**
     * Returns a pre-computed digest of the entire codebase as a byte array,
     * or {@code null} if not supported.
     *
     * <p>The digest is computed as follows: for each JAR URL in the codebase
     * annotation (in order, excluding directory URLs), the per-JAR digest is
     * computed using the algorithm returned by
     * {@link #getCodebaseDigestAlgorithm()}.  The returned digest is then the
     * result of applying the same algorithm to the concatenation of all
     * per-JAR digest bytes (hash-of-hashes).  For a codebase with a single
     * JAR this is equivalent to {@code digest(digest(jarContent))}.
     *
     * <p>This combined digest is used only as an overall integrity check.
     * For per-JAR {@code DigestGrant} issuance, use
     * {@link #getCodebaseJarDigests()} together with
     * {@link #getCodebaseJarDigestOffsets()}.
     *
     * <p>The default implementation returns {@code null}.  Implementations
     * should pre-compute and cache this value at service startup.
     *
     * <p>The digest is transmitted over the already-authenticated SPIFFE/TLS
     * channel and is therefore integrity-protected by the transport layer.
     *
     * @return the codebase digest bytes, or {@code null} if not supported
     * @throws IOException if a communication problem occurs
     */
    public default byte[] getCodebaseDigest() throws IOException {
        return null;
    }

    /**
     * Returns the per-JAR digest bytes for each non-directory JAR URL in the
     * codebase, in codebase order (excluding directory URLs), or {@code null}
     * if not supported.
     *
     * <p>The array has one element per non-directory JAR URL.  Each element is
     * the raw digest bytes of that JAR's content, computed using the algorithm
     * returned by {@link #getCodebaseDigestAlgorithm()}.  The corresponding
     * index of each JAR in the full codebase URL array is given by
     * {@link #getCodebaseJarDigestOffsets()}.
     *
     * <p>These per-JAR digests are required to create properly scoped
     * {@code DigestGrant}s: because {@code DigestCodeSource} is per-JAR, each
     * grant must carry a single JAR's digest.  A combined hash-of-hashes
     * cannot be used for {@code DigestGrant} purposes.
     *
     * <p>The default implementation returns {@code null}.  Implementations
     * should pre-compute and cache these values at service startup.
     *
     * <p>The digests are transmitted over the already-authenticated SPIFFE/TLS
     * channel and are therefore integrity-protected by the transport layer.
     *
     * @return an array of per-JAR digest byte arrays (one per non-directory
     *         JAR URL, in codebase order), or {@code null} if not supported
     * @throws IOException if a communication problem occurs
     * @see #getCodebaseJarDigestOffsets()
     * @see #getCodebaseDigestAlgorithm()
     */
    public default byte[][] getCodebaseJarDigests() throws IOException {
        return null;
    }

    /**
     * Returns the index of each non-directory JAR URL (from the full codebase
     * URL array returned by {@link #getClassAnnotation()}) that corresponds to
     * the matching entry in {@link #getCodebaseJarDigests()}, or {@code null}
     * if not supported.
     *
     * <p>For example, if the codebase URL array is
     * {@code [dir/, a.jar, dir2/, b.jar]} then the offsets array would be
     * {@code [1, 3]}, indicating that digest index 0 corresponds to URL index 1
     * ({@code a.jar}) and digest index 1 corresponds to URL index 3
     * ({@code b.jar}).
     *
     * <p>The array must be the same length as the array returned by
     * {@link #getCodebaseJarDigests()}.
     *
     * <p>The default implementation returns {@code null}.  Implementations
     * should pre-compute and cache these values at service startup.
     *
     * @return an {@code int[]} of URL-array offsets, one per non-directory JAR,
     *         or {@code null} if not supported
     * @throws IOException if a communication problem occurs
     * @see #getCodebaseJarDigests()
     */
    public default int[] getCodebaseJarDigestOffsets() throws IOException {
        return null;
    }

}
