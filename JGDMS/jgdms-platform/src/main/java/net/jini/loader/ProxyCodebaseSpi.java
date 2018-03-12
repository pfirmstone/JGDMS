/*
 * Copyright 2018 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.loader;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.IntegrityEnforcement;

/**
 * A provider used by @{link org.apache.river.api.io.AtomicMarshalInputStream}
 * to allow third party smart proxy's found in a stream to have 
 * {@link net.jini.core.constraint.MethodConstraints} applied by the client
 * to {@link CodebaseAccessor} methods and allow the client to obtain
 * current information about the code base
 * required by the smart proxy {@link MarshalledInstance) and 
 * provide it with a ClassLoader, unique
 * to it's {@link java.lang.reflect.InvocationHandler} instance.
 * 
 * This interface also exists to avoid code base annotation loss when smart 
 * proxy instances are re-serialized, by consulting the original
 * export node, using {@link CodebaseAccessor}.
 * 
 * @see org.apache.river.api.io.AtomicMarshalOutputStream
 * @see org.apache.river.api.io.AtomicMarshalInputStream
 * @see org.apache.river.api.io.AtomicMarshalledInstance
 * 
 * @author peter
 */
public interface ProxyCodebaseSpi {

    /**
     * 
     * @param bootstrapProxy an instance of {@link net.jini.core.constraint.RemoteMethodControl} and
     * {@link net.jini.security.proxytrust.TrustEquivalence}
     * @param smartProxy
     * @param parentLoader default loader of the current stream.
     * @param verifierLoader verifier loader of the current stream.
     * @param context the {@link net.jini.io.ObjectStreamContext} may contain
     * client {@link net.jini.core.constraint.MethodConstraints} to apply to
     * {@link CodebaseAccessor} methods.  The context should also be passed
     * to the {@link MarshalledInstance} get method.
     * @return
     * @throws IOException 
     * @throws java.lang.ClassNotFoundException 
     */
    public Object resolve(
	    CodebaseAccessor bootstrapProxy,
	    MarshalledInstance smartProxy,
	    ClassLoader parentLoader,
	    ClassLoader verifierLoader,
	    Collection context) throws IOException, ClassNotFoundException;
    
    /**
     *
     * @param serviceClass
     * @param streamLoader
     * @return
     */
    public boolean substitute(Class serviceClass, ClassLoader streamLoader);
    
    public static class Util {
	
	public static boolean verifyCodebaseIntegrity(Collection context) {
	    if (context != null){
		Iterator it = context.iterator();
		while (it.hasNext()){
		    Object next = it.next();
		    if (next instanceof IntegrityEnforcement){
			return ((IntegrityEnforcement) next).integrityEnforced();
		    }
		}
	    }
	    return false;
	}
    }
    
}