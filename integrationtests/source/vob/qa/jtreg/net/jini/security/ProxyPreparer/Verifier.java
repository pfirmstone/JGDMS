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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;

/**
 * A TrustVerifier that trusts RS.Trusted and throws RemoteException for
 * RS.VerifyThrows.
 */
public class Verifier extends UnitTestUtilities implements TrustVerifier {
    private static final String src =
	System.getProperty("test.src", ".") + File.separator;

    private static final String RESOURCE =
	"META-INF/services/" +
	"net.jini.security.TrustVerifier";

    /**
     * A ClassLoader that includes resources that specify this class as a trust
     * verifier.
     */
    static final ClassLoader CLASSLOADER =
	new ClassLoader(Verifier.class.getClassLoader()) {
	    protected Enumeration findResources(String name)
		throws IOException
	    {
		if (!RESOURCE.equals(name)) {
		    return super.findResources(name);
		}
		return new Enumeration() {
		    private boolean done;
		    public boolean hasMoreElements() { return !done; }
		    public Object nextElement() {
			if (done) {
			    throw new NoSuchElementException();
			}
			done = true;
			try {
			    return new URL(
				"file:" + src + "Verifier.resource");
			} catch (MalformedURLException e) {
			    throw unexpectedException(e);
			}
		    }
		};
	    }
	};

    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	if (obj instanceof RS.Trusted) {
	    return true;
	} else if (obj instanceof RS.VerifyThrows) {
	    throw new RemoteException("A remote exception occurred");
	}
	return false;
    }
}
