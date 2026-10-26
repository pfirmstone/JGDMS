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

package org.apache.river.discovery.spiffe.sha512withecdsa;

/**
 * Constants for the net.jini.discovery.spiffe.SHA512withECDSA format,
 * intended for P-384 keys and server nodes.
 */
class Constants {

    /* the name of this discovery format */
    static final String FORMAT_NAME = "net.jini.discovery.spiffe.SHA512withECDSA";

    /* defined in Appendix A of the Java Cryptography Architecture spec */
    static final String SIGNATURE_ALGORITHM = "SHA512withECDSA";

    /*
     * Max length of the ASN.1 signature encoding for P-384: a DER SEQUENCE of
     * two 48-byte INTEGERs, 2 + 2*(2 + 49) = 104.  P-521 would need 140.
     */
    static final int MAX_SIGNATURE_LEN = 104;

    /*
     * "EC" -- not "ECDSA".  Compared against PrivateKey.getAlgorithm(), which
     * SunEC reports as "EC".  See the SHA256 variant for the full rationale.
     */
    static final String KEY_ALGORITHM = "EC";

    /*
     * id-ecPublicKey (RFC 5480) -- the SubjectPublicKeyInfo algorithm OID, not a
     * signature OID.  Same rationale as the SHA256 variant.
     */
    static final String KEY_ALGORITHM_OID = "1.2.840.10045.2.1";

    private Constants() {
    }
}
