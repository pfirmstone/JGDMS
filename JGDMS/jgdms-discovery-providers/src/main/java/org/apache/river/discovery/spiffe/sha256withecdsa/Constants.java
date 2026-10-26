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

package org.apache.river.discovery.spiffe.sha256withecdsa;

/**
 * Constants for the net.jini.discovery.spiffe.SHA256withECDSA format,
 * intended for P-256 keys and embedded devices.
 */
class Constants {

    /* the name of this discovery format */
    static final String FORMAT_NAME = "net.jini.discovery.spiffe.SHA256withECDSA";

    /* defined in Appendix A of the Java Cryptography Architecture spec */
    static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /*
     * Max length of the ASN.1 signature encoding for P-256.  A P-256 ECDSA
     * signature is a DER SEQUENCE of two 32-byte INTEGERs:
     *   2 (SEQUENCE tag + len) + 2*(2 (INTEGER tag + len) + 33) = 72
     * where 33 allows for a leading zero byte when the high bit is set.  This is
     * tighter than the 128 used by the x500 formats, which matters for the
     * datagram budget once the leaf certificate is also carried in the packet.
     */
    static final int MAX_SIGNATURE_LEN = 72;

    /*
     * "EC" -- not "ECDSA".  This is compared against
     * PrivateKey.getAlgorithm(), and SunEC (which the DirtyChai JDK uses)
     * reports "EC".  "ECDSA" is a BouncyCastle spelling; the x500 formats use it
     * and consequently reject every SunEC credential.
     */
    static final String KEY_ALGORITHM = "EC";

    /*
     * id-ecPublicKey (RFC 5480) -- the SubjectPublicKeyInfo algorithm OID for EC
     * keys, which is what X509CertSelector.setSubjectPublicKeyAlgID() matches
     * against.  It must be a key OID, never a signature OID.
     */
    static final String KEY_ALGORITHM_OID = "1.2.840.10045.2.1";

    private Constants() {
    }
}
