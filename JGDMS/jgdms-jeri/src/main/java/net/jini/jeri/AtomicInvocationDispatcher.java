/*
 * Copyright 2018 peter.
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
package net.jini.jeri;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import net.jini.core.constraint.MethodConstraints;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;

/**
 *
 * @author peter
 */
public class AtomicInvocationDispatcher extends BasicInvocationDispatcher {
    
    private final boolean useAnnotations;

    public AtomicInvocationDispatcher(Collection methods,
				      ServerCapabilities serverCapabilities,
				      MethodConstraints serverConstraints,
				      Class permissionClass,
				      ClassLoader loader,
				      boolean useAnnotations) throws ExportException 
    {
	super(methods, serverCapabilities, serverConstraints, permissionClass, loader);
	this.useAnnotations = useAnnotations;
    }
    
    /**
     * Returns a new marshal input stream to use to read objects from the
     * request input stream obtained by invoking the {@link
     * InboundRequest#getRequestInputStream getRequestInputStream} method
     * on the given <code>request</code>.
     *
     * <p><code>AtomicInvocationDispatcher</code> implements this method as
     * follows:
     *
     * <p>First, a class loader is selected to use as the
     * <code>defaultLoader</code> and the <code>verifierLoader</code> for
     * the marshal input stream instance.  If the class loader specified at
     * construction is not <code>null</code>, the selected loader is that
     * loader.  Otherwise, if a security manager exists, its {@link
     * SecurityManager#checkPermission checkPermission} method is invoked
     * with the permission <code>{@link
     * RuntimePermission}("getClassLoader")</code>; this invocation may
     * throw a <code>SecurityException</code>.  If the above security check
     * succeeds, the selected loader is the class loader of
     * <code>impl</code>'s class.
     *
     * <p>This method returns a new {@link MarshalInputStream} instance
     * constructed with the input stream (obtained from the
     * <code>request</code> as specified above) for the input stream
     * <code>in</code>, the selected loader for <code>defaultLoader</code>
     * and <code>verifierLoader</code>, the boolean <code>integrity</code>
     * for <code>verifyCodebaseIntegrity</code>, and an unmodifiable view
     * of <code>context</code> for the <code>context</code> collection.
     * The {@link MarshalInputStream#useCodebaseAnnotations
     * useCodebaseAnnotations} method is invoked on the created stream
     * before it is returned.
     *
     * <p>A subclass can override this method to control how the marshal input
     * stream is created or implemented.
     *
     * @param	impl the remote object
     * @param	request the inbound request
     * @param	integrity <code>true</code> if object integrity is being
     * 		enforced for the remote call, and <code>false</code> otherwise
     * @param	context the server context
     * @return	a new marshal input stream for unmarshalling a call request
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    @Override
    protected ObjectInputStream
        createMarshalInputStream(Object impl,
				 final InboundRequest request,
				 final boolean integrity,
				 Collection context)
	throws IOException
    {
	final ClassLoader streamLoader = getStreamLoader(impl);
	
	final Collection unmodContext = Collections.unmodifiableCollection(context);
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction<ObjectInputStream>(){
		
		@Override
		public ObjectInputStream run() throws Exception {
		    return AtomicMarshalInputStream.create(request.getRequestInputStream(),
			    streamLoader, integrity,
			    streamLoader, unmodContext, useAnnotations);
		}
    
	    });
	} catch (PrivilegedActionException ex) {
	    Exception cause = ex.getException();
	    if (cause instanceof IOException) throw (IOException) cause;
	    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
	    throw new IOException ("Unable to create ObjectOutputStream ",ex);
	}
    }
    
    /**
     * Returns a new marshal output stream to use to write objects to the
     * response output stream obtained by invoking the {@link
     * InboundRequest#getResponseOutputStream getResponseOutputStream}
     * method on the given <code>request</code>.
     *
     * <p>This method will be called with a <code>null</code>
     * <code>method</code> argument if an <code>IOException</code> occurred
     * when reading method information from the incoming call stream.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method to
     * return a new {@link MarshalOutputStream} instance constructed with
     * the output stream obtained from the <code>request</code> as
     * specified above and an unmodifiable view of the given
     * <code>context</code> collection.
     *
     * <p>A subclass can override this method to control how the marshal output
     * stream is created or implemented.
     *
     * @param	impl the remote object
     * @param   method the possibly-<code>null</code> <code>Method</code>
     *		instance corresponding to the interface method invoked on
     *		the remote object
     * @param	request the inbound request
     * @param	context the server context
     * @return	a new marshal output stream for marshalling a call response
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if <code>impl</code>,
     *		<code>request</code>, or <code>context</code> is
     *		<code>null</code>
     **/
    @Override
    protected ObjectOutputStream
        createMarshalOutputStream(final Object impl,
				  Method method,
				  final InboundRequest request,
				  final Collection context)
	throws IOException
    {
	if (impl == null) {
	    throw new NullPointerException();
	}
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction<ObjectOutputStream>(){
		
		@Override
		public ObjectOutputStream run() throws IOException {
		    return new AtomicMarshalOutputStream(
			    request.getResponseOutputStream(), 
			    getStreamLoader(impl), 
			    Collections.unmodifiableCollection(context), 
			    useAnnotations
		    );
		}
							  
	    });
	} catch (PrivilegedActionException ex) {
	    Exception cause = ex.getException();
	    if (cause instanceof IOException) throw (IOException) cause;
	    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
	    throw new IOException ("Unable to create ObjectOutputStream ",ex);
	}
	
	
    }
        
    // So developers can see this class in stack traces.
    @Override
    public void dispatch(Remote impl,
                     InboundRequest request,
                     Collection context)
    {
        super.dispatch(impl,request,context);
    }
    
}
