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

package org.apache.river.discovery.x500.sha512withecdsa;

/**
 * Constants used by net.jini.discovery.x500.SHA512withECDSA format providers.
 */
class Constants {

    /* discovery format name */
    static final String FORMAT_NAME = "net.jini.discovery.x500.SHA512withECDSA";

    /* defined in Appendix A of the Java Cryptography Architecture spec */
    static final String SIGNATURE_ALGORITHM = "SHA512withECDSA";

    /* max length of ASN.1 signature encoding  */
    static final int MAX_SIGNATURE_LEN = 128;

    /*
     * Key algorithm name as reported by PrivateKey.getAlgorithm().
     *
     * This must be "EC", the JCA standard name (JCA spec, Appendix A), not
     * "ECDSA".  "ECDSA" is a BouncyCastle-ism; SunEC -- and every other JCA
     * provider following the standard names -- reports "EC".
     * X500Provider.getPrivateCredentials() selects a signing credential with
     * keyAlgorithm.equals(cred.getPrivateKey().getAlgorithm()), so with
     * "ECDSA" here no EC key from the standard providers ever matched, and
     * this format silently had no usable credentials.
     */
    static final String KEY_ALGORITHM = "EC";

    /*
     * OID for EC public keys (id-ecPublicKey), as specified in RFC 5480.
     *
     * This must be the SubjectPublicKeyInfo *key* OID, not a signature OID:
     * X500Provider.getCertificate0() passes it to
     * X509CertSelector.setSubjectPublicKeyAlgID(), which matches against the
     * certificate's SubjectPublicKeyInfo algorithm.  This previously held
     * 1.2.840.10045.4.3.4 (ecdsa-with-SHA512, a signature OID from RFC 5758),
     * which no certificate carries in that field, so the selector never matched
     * and the cert lookup silently returned null.
     */
    static final String KEY_ALGORITHM_OID = "1.2.840.10045.2.1";

    private Constants() {
    }
}
