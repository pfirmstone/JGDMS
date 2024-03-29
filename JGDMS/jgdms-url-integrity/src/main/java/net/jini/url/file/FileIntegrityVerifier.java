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

package net.jini.url.file;

import java.net.URL;
import net.jini.security.IntegrityVerifier;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Requirement;

/**
 * Integrity verifier for FILE URLs. This class is intended to be specified
 * in a resource to configure the operation of
 * {@link net.jini.security.Security#verifyCodebaseIntegrity
 * Security.verifyCodebaseIntegrity}.
 * <p>
 * Do not use this verifier if your operating system is configured to
 * mount remote file systems and the underlying file system protocols do
 * not provide adequate data integrity or authentication of the remote hosts.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@Requirement(
	namespace="osgi.extender",
	filter="(osgi.extender=osgi.serviceloader.registrar)")
@Capability(
	namespace="osgi.serviceloader",
	name="net.jini.security.IntegrityVerifier")
public class FileIntegrityVerifier implements IntegrityVerifier {
    /**
     * Returns <code>true</code> if the specified URL uses the "file"
     * protocol and the host is <code>null</code>, empty, "~", or "localhost";
     * returns <code>false</code> otherwise.
     *
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public boolean providesIntegrity(URL url) {
	if (!"file".equals(url.getProtocol())) {
	    return false;
	}
	String host = url.getHost();
	return (host == null || host.equals("") ||
		host.equals("~") || host.equals("localhost"));
    }
}
