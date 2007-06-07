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

package com.sun.jini.discovery.x500.sha1withrsa;

/**
 * Constants used by net.jini.discovery.x500.SHA1withRSA format providers.
 */
class Constants {

    /* discovery format name */
    static final String FORMAT_NAME = "net.jini.discovery.x500.SHA1withRSA";

    /* defined in Appendix A of the Java Cryptography Architecture spec */
    static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    /* length of PKCS #1 block, described in RFC 2313 */
    static final int MAX_SIGNATURE_LEN = 128;

    /* defined in Appendix A of the Java Cryptography Architecture spec */
    static final String KEY_ALGORITHM = "RSA";

    /* OID for RSA keys, as specified in RFC 2459 */
    static final String KEY_ALGORITHM_OID = "1.2.840.113549.1.1.1";

    private Constants() {
    }
}
