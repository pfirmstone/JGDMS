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
/* @test 
 * @summary Tests KeyStores.getX500Principal
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm/policy=policy TestGetX500Principal
 */

import org.apache.river.config.KeyStores;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.security.auth.x500.X500Principal;

public class TestGetX500Principal extends BasicTest {

    static final String src =
	System.getProperty("test.src", ".") + File.separator;

    static {
	if (System.getProperty("java.security.policy") == null) {
	    System.setProperty("java.security.policy", src + "policy");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    private static KeyStore defaultKeyStore;
    static {
	try {
	    defaultKeyStore =
		KeyStores.getKeyStore(src + "keystore", null);
	} catch (GeneralSecurityException e) {
	    throw unexpectedException(e);
	} catch (IOException e) {
	    throw unexpectedException(e);
	}
    }

    static Test[] tests = {
	new TestGetX500Principal(null, null, NullPointerException.class),
	new TestGetX500Principal("foo", null, NullPointerException.class),
	new TestGetX500Principal(null, defaultKeyStore,
				 NullPointerException.class),
	new TestGetX500Principal("not-found", defaultKeyStore, null),
	new TestGetX500Principal("foo", defaultKeyStore, X500Principal.class)
    };

    private final String alias;
    private final KeyStore keystore;

    public static void main(String[] args) throws Exception {
	test(tests);
    }

    private TestGetX500Principal(String alias,
				 KeyStore keystore,
				 Class resultType)
    {
	super(alias + ", " + keystore, resultType);
	this.alias = alias;
	this.keystore = keystore;
    }

    public Object run() {
	try {
	    return KeyStores.getX500Principal(alias, keystore);
	} catch (Exception e) {
	    return e;
	}
    }

    public void check(Object result) {
	if (result != null) {
	    result = result.getClass();
	}
	if (result != getCompareTo()) {
	    if (getCompareTo() == null) {
		throw new FailedException("Should be null");
	    } else {
		throw new FailedException(
		    "Should be of type " + getCompareTo());
	    }
	}
    }
}
