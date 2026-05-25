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
     * Returns a pre-computed flat byte array containing the individual content
     * digests of all non-directory JAR files in the codebase, concatenated in
     * codebase order (excluding directory URLs), or {@code null} if not
     * supported.
     *
     * <p>Unlike a combined hash-of-hashes, the returned array is the raw
     * concatenation of individual per-JAR digest bytes.  The byte offset at
     * which each individual digest begins within this array is given by
     * {@link #getDigestOffsets()}.  This representation is required for
     * issuing proper per-JAR {@code DigestGrant}s: because
     * {@code DigestCodeSource} (DirtyChai) is per-JAR, each grant must carry
     * a single JAR's digest.
     *
     * <p>For example, for a codebase with two JARs whose SHA-256 digests are
     * {@code d0} (32 bytes) and {@code d1} (32 bytes), this method returns
     * a 64-byte array {@code d0 || d1}, and {@link #getDigestOffsets()} returns
     * {@code [0, 32]}.
     *
     * <p>The default implementation returns {@code null}.  Implementations
     * should pre-compute and cache this value at service startup.
     *
     * <p>The data is transmitted over the already-authenticated SPIFFE/TLS
     * channel and is therefore integrity-protected by the transport layer.
     *
     * @return the flat concatenation of per-JAR digest bytes, or {@code null}
     *         if not supported
     * @throws IOException if a communication problem occurs
     * @see #getDigestOffsets()
     * @see #getCodebaseDigestAlgorithm()
     */
    public default byte[] getCodebaseDigest() throws IOException {
        return null;
    }

    /**
     * Returns the start byte offsets of each individual per-JAR digest within
     * the flat digest array returned by {@link #getCodebaseDigest()}, or
     * {@code null} if not supported.
     *
     * <p>Element {@code i} of the returned array is the byte index in the flat
     * array at which the digest of the {@code i}-th non-directory JAR begins.
     * The digest for JAR {@code i} occupies bytes
     * {@code flat[offsets[i] .. (i+1 < offsets.length ? offsets[i+1] : flat.length) - 1]}.
     *
     * <p>For example, for a codebase with two JARs with 32-byte SHA-256
     * digests, this method returns {@code [0, 32]}.
     *
     * <p>The number of entries equals the number of non-directory JAR URLs in
     * the codebase (directory URLs are excluded in the same way as for
     * {@link #getCodebaseDigest()}).
     *
     * <p>The default implementation returns {@code null}.  Implementations
     * should pre-compute and cache this value at service startup.
     *
     * @return an {@code int[]} of start byte offsets into the flat digest
     *         array, one per non-directory JAR, or {@code null} if not supported
     * @throws IOException if a communication problem occurs
     * @see #getCodebaseDigest()
     */
    public default int[] getDigestOffsets() throws IOException {
        return null;
    }

}
