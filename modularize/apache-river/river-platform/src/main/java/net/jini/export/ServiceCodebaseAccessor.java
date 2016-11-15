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
import net.jini.core.constraint.Integrity;
import net.jini.loader.ClassLoading;
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
 * provider isn't installed.
 * 
 * @see RevocablePolicy
 * @see PermissionGrant
 */
public interface ServiceCodebaseAccessor extends Remote {
    
    /**
     * Obtains the service class annotation as defined in
     * {@link ClassLoading#getClassAnnotation(Class)}.
     * 
     * When an Integrity constraint is specified, 
     * 
     * @return the codebase annotation.
     * @throws IOException 
     * @see ClassLoading
     * @see Integrity
     */
    public String getClassAnnotation() throws IOException;
    
    /**
     * Get the CertificateFactory type.
     * 
     * @return CertificateFactory type or null.
     * @throws IOException 
     * @see CertificateFactory#getInstance(java.lang.String) 
     */
    public String getCertFactoryType() throws IOException;
    
    /**
     * Get the CertPath encoding;
     * @return CertPath encoding or null.
     * @throws IOException 
     * @see CertPath#CertPath(java.lang.String) 
     */
    public String getCertPathEncoding() throws IOException;
    
    /**
     * The byte array can be passed to a ByteArrayInputStream, which can be
     * passed to a CertificateFactory to generate a Collection of Certificates,
     * or CertPath.
     * 
     * @return a byte array containing certificates or null.
     * @throws IOException 
     * @see ByteArrayInputStream
     * @see CertificateFactory#generateCertPath(java.io.InputStream) 
     * @see CertificateFactory#generateCertificates(java.io.InputStream) 
     */
    public byte [] getEncodedCerts() throws IOException;
    
}
