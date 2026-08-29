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

package org.apache.river.discovery.x500.sha256withdsa;

/**
 * Constants used by net.jini.discovery.x500.SHA1withDSA format providers.
 */
class Constants {

    /* discovery format name */
    static final String FORMAT_NAME = "net.jini.discovery.x500.SHA256withDSA";

    /* defined in Appendix A of the Java Cryptography Architecture spec */
    static final String SIGNATURE_ALGORITHM = "SHA256withDSA";

    /* max length of ASN.1 signature encoding specified in RFC 2459 */
    static final int MAX_SIGNATURE_LEN = 128;

    /* defined in Appendix A of the Java Cryptography Architecture spec */
    static final String KEY_ALGORITHM = "DSA";

    /*
     * OID for DSA public keys (id-dsa), as specified in RFC 3279.
     *
     * This must be the SubjectPublicKeyInfo *key* OID, not a signature OID:
     * X500Provider.getCertificate0() passes it to
     * X509CertSelector.setSubjectPublicKeyAlgID(), which matches against the
     * certificate's SubjectPublicKeyInfo algorithm.  This previously held
     * 2.16.840.1.101.3.4.3.2 (id-dsa-with-sha256, a signature OID from RFC 5758),
     * which no certificate carries in that field, so the selector never matched
     * and the cert lookup silently returned null.  Matches sha1withdsa, which
     * has always carried the correct value.
     */
    static final String KEY_ALGORITHM_OID = "1.2.840.10040.4.1";

    private Constants() {
    }
}
