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

package com.sun.jini.config;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationFile;
import net.jini.jeri.ssl.SslServerEndpoint;

/**
 * Provides static methods for manipulating instances of {@link KeyStore}
 * conveniently from within the source of a {@link Configuration}. This class
 * cannot be instantiated.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class KeyStores {

    /** This class cannot be instantiated. */
    private KeyStores() {
	new AssertionError();
    }

    /**
     * Returns a <code>KeyStore</code> initialized with contents read from a
     * location specified as a file or URL. This method provides a convenient
     * way to refer to keystores from within the source for a configuration,
     * which may then be used with {@link #getX500Principal getX500Principal}
     * to refer to principals. <p>
     *
     * For example, a deployer that was using {@link SslServerEndpoint} might
     * use the following in the source for a {@link ConfigurationFile} to
     * supply principals for use in security constraints: <p>
     *
     * <pre>
     *  Client {
     *      private static users = KeyStores.getKeyStore("users.ks", null);
     *      private static client = KeyStores.getX500Principal("client", users);
     *      //...
     *  }
     * </pre>
     *
     * @param location the file name or URL containing the
     * <code>KeyStore</code> contents
     * @param type the type of <code>KeyStore</code> to create, or
     * <code>null</code> for the default type
     * @return the <code>KeyStore</code>, with contents read from
     * <code>location</code>
     * @throws GeneralSecurityException if there are problems with the contents
     * @throws IOException if an I/O error occurs when reading from
     * <code>location</code>
     * @throws NullPointerException if <code>location</code> is
     * <code>null</code>
     * @see #getX500Principal getX500Principal
     */
    public static KeyStore getKeyStore(String location, String type)
	throws GeneralSecurityException, IOException
    {
	if (location == null) {
	    throw new NullPointerException("location cannot be null");
	}
	KeyStore keystore;
	InputStream in = null;
	try {
	    try {
		URL url = new URL(location);
		in = url.openStream();
	    } catch (MalformedURLException e) {
		in = new FileInputStream(location);
	    }
	    in = new BufferedInputStream(in);
	    keystore = KeyStore.getInstance(
		type != null ? type : KeyStore.getDefaultType());
	    keystore.load(in, null);
	    return keystore;
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * Returns the <code>X500Principal</code> for the alias in a
     * <code>KeyStore</code>; or <code>null</code> if the alias is not found,
     * if the alias is not associated with a certificate, or if the certificate
     * is not an {@link X509Certificate}. This method provides a convenient way
     * to refer to principals from within the source for a configuration by
     * specifying aliases when used with {@link #getKeyStore getKeystore}. <p>
     *
     * For example, a deployer that was using {@link SslServerEndpoint} might
     * use the following in the source for a {@link ConfigurationFile} to
     * supply principals for use in security constraints: <p>
     *
     * <pre>
     *  Client {
     *      private static users = KeyStores.getKeyStore("users.ks", null);
     *      private static client = KeyStores.getX500Principal("client", users);
     *      //...
     *  }
     * </pre>
     *
     * @param alias the alias
     * @param keystore the <code>KeyStore</code>
     * @return the <code>X500Principal</code> or <code>null</code>
     * @throws KeyStoreException if the keystore has not been initialized
     * (loaded)
     * @throws NullPointerException if either argument is <code>null</code>
     * @see #getKeyStore getKeyStore
     */
    public static X500Principal getX500Principal(String alias,
						 KeyStore keystore)
	throws KeyStoreException
    {
	if (alias == null) {
	    throw new NullPointerException("alias is null");
	} else if (keystore == null) {
	    throw new NullPointerException("keystore is null");
	}
	Certificate cert = keystore.getCertificate(alias);
	if (cert instanceof X509Certificate) {
	    return ((X509Certificate) cert).getSubjectX500Principal();
	}
	return null;
    }
}
