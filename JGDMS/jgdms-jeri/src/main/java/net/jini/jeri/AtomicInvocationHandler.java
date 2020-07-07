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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.jini.core.constraint.MethodConstraints;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.jeri.internal.runtime.Util;

/**
 *
 * @author peter
 */
@AtomicSerial
public class AtomicInvocationHandler extends BasicInvocationHandler {
    private static final long serialVersionUID = 1L;
    
    /**
     * @serial
     */
    private final boolean useCodebaseAnnotations;
    private final Compression compression;

    public AtomicInvocationHandler(AtomicInvocationHandler other, MethodConstraints clientConstraints) {
	super(other, clientConstraints);
	this.useCodebaseAnnotations = other.useCodebaseAnnotations;
        this.compression = other.compression;
    }

    public AtomicInvocationHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
	super(arg);
	this.useCodebaseAnnotations = arg.get("useCodebaseAnnotations", false);
        this.compression = arg.get("compression", Compression.NONE, Compression.class);
    }

    public AtomicInvocationHandler(ObjectEndpoint oe,
				   MethodConstraints serverConstraints,
				   boolean useCodebaseAnnotations) 
    {
	this(oe, serverConstraints, useCodebaseAnnotations, Compression.NONE);
    }
    
    public AtomicInvocationHandler(ObjectEndpoint oe,
				   MethodConstraints serverConstraints,
				   boolean useCodebaseAnnotations,
                                   Compression compress) 
    {
	super(oe, serverConstraints);
	this.useCodebaseAnnotations = useCodebaseAnnotations;
        this.compression = compress;
    }
    
    /**
     * Unlike BasicInvocationHandler, AtomicInvocationHandler doesn't consider
     * client constraints in equals comparisons, only the object endpoint and
     * server constraints.
     * 
     * AtomicMarshalInputStream assigns client constraints during un-marshaling,
     * furthermore the client may decide to reassign client constraints.  This
     * is unique to AtomicMarshalInputStream and is designed to prevent one 
     * service proxy from utilising and de-serializing other proxy's without
     * client applied constraints.  This causes problems with proxy identity.
     * 
     * @param o
     * @return 
     */
    @Override
    public boolean equals(Object o){
	if (this == o) return true;
	if (!(o instanceof AtomicInvocationHandler)) return false;
	AtomicInvocationHandler other = (AtomicInvocationHandler) o;
	return
		useCodebaseAnnotations == other.useCodebaseAnnotations &&
	    Util.sameClassAndEquals(getObjectEndpoint(), other.getObjectEndpoint()) 
	    && Util.equals(getServerConstraints(), other.getServerConstraints())
                && compression.equals(other.compression);
    }
    
   @Override
    public int hashCode() {
	int hash = 7;
	ObjectEndpoint oe = getObjectEndpoint();
	hash = 67 * hash + oe.hashCode();
        hash = 67 * hash + compression.hashCode();
	return hash;
    }
  
    /**
     * Returns a new {@link ObjectOutputStream} instance to use to write
     * objects to the request output stream obtained by invoking the {@link
     * OutboundRequest#getRequestOutputStream getRequestOutputStream} method
     * on the given <code>request</code>.
     *
     * <p><code>AtomicInvocationHandler</code> implements this method
     * to return a new {@link MarshalOutputStream} instance
     * constructed with the output stream obtained from
     * <code>request</code> as specified above and an unmodifiable
     * view of the supplied <code>context</code> collection.
     *
     * <p>A subclass can override this method to control how the
     * marshal input stream is created or implemented.
     *
     * @param	proxy the proxy instance
     * @param	method the remote method invoked
     * @param	request the outbound request
     * @param	context the client context
     * @return	a new {@link ObjectOutputStream} instance for marshalling
     *		a call request
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    @Override
    protected ObjectOutputStream
        createMarshalOutputStream(final Object proxy,
				  Method method,
				  OutboundRequest request,
				  Collection context)
	throws IOException
    {
	if (proxy == null || method == null) {
	    throw new NullPointerException();
	}
	final OutputStream out;
        switch (compression){
            case NONE: 
                out = request.getRequestOutputStream();
                break;
            case DEFLATE:
                out  = new DeflaterOutputStream(request.getRequestOutputStream());
                break;
            case DEFLATE_BEST_COMPRESSION:
                out = new DeflaterOutputStream(
                        request.getRequestOutputStream(),
                        new Deflater(Deflater.BEST_COMPRESSION)
                );
                break;
            case DEFLATE_BEST_SPEED:
                out = new DeflaterOutputStream(
                        request.getRequestOutputStream(),
                        new Deflater(Deflater.BEST_SPEED)
                );
                break;
            default:
                out = request.getRequestOutputStream();
            
        }
	final Collection unmodContext = Collections.unmodifiableCollection(context);
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction<MarshalOutputStream>(){
		
		@Override
		public MarshalOutputStream run() throws Exception {
		    return new AtomicMarshalOutputStream(
			    out,
			    getProxyLoader(proxy.getClass()),
			    unmodContext,
			    useCodebaseAnnotations
		    );
		}
		
	    });
	} catch (PrivilegedActionException ex) {
	    Exception e = ex.getException();
	    if (e instanceof IOException) throw (IOException)e;
	    if (e instanceof RuntimeException) throw (RuntimeException)e;
	    throw new IOException("Exception thrown during construction", ex);
	}
	
    }
							  
    /**
     * Returns a new {@link ObjectInputStream} instance to use to read
     * objects from the response input stream obtained by invoking the {@link
     * OutboundRequest#getResponseInputStream getResponseInputStream} method
     * on the given <code>request</code>.
     *
     * <p><code>AtomicInvocationHandler</code> implements this method
     * to return a new {@link MarshalInputStream} instance constructed
     * with the input stream obtained from <code>request</code> as
     * specified above for the input stream <code>in</code>, the class
     * loader of <code>proxy</code>'s class for
     * <code>defaultLoader</code> and <code>verifierLoader</code>,
     * this method's <code>integrity</code> argument for
     * <code>verifyCodebaseIntegrity</code>, and an unmodifiable view
     * of <code>context</code> for the <code>context</code>
     * collection.  The {@link
     * MarshalInputStream#useCodebaseAnnotations
     * useCodebaseAnnotations} method is invoked on the created stream
     * before it is returned.
     *
     * <p>An exception is thrown if <code>proxy</code> is not an instance
     * of a dynamic proxy class containing this invocation handler.
     *
     * <p>A subclass can override this method to control how the
     * marshal input stream is created or implemented.
     *
     * @param	proxy the proxy instance
     * @param	method the remote method invoked
     * @param	request the outbound request
     * @param	integrity whether or not to verify codebase integrity
     * @param	context the client context
     * @return	a new {@link ObjectInputStream} instance for unmarshalling
     *		a call response
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    @Override
    protected ObjectInputStream
        createMarshalInputStream(Object proxy,
				 Method method,
				 final OutboundRequest request,
				 final boolean integrity,
				 Collection context)
	throws IOException
    {
	if (method == null) {
	    throw new NullPointerException();
	}
	if (Proxy.getInvocationHandler(proxy) != this) {
	    throw new IllegalArgumentException("not proxy for this");
	}
	final ClassLoader proxyLoader = getProxyLoader(proxy.getClass());
	final Collection unmodContext = Collections.unmodifiableCollection(context);
	
	ObjectInputStream in;
	try {
	    in = AccessController.doPrivileged(new PrivilegedExceptionAction<ObjectInputStream>(){
		
		@Override
		public ObjectInputStream run() throws Exception {
                    InputStream in = request.getResponseInputStream();
                    switch (compression){
                        case NONE:
                            break;
                        case DEFLATE:
                            in = new InflaterInputStream(in);
                            break;
                        case DEFLATE_BEST_COMPRESSION:
                            in = new InflaterInputStream(in);
                            break;
                        case DEFLATE_BEST_SPEED:
                            in = new InflaterInputStream(in);
                            break;
                        default:
                            break;
                            
                    }
		    return AtomicMarshalInputStream.create(in,
				    proxyLoader, integrity, proxyLoader,
				    unmodContext, useCodebaseAnnotations);
		}
		
	    });
	} catch (PrivilegedActionException ex) {
	    Exception e = ex.getException();
	    if (e instanceof IOException) throw (IOException) e;
	    if (e instanceof RuntimeException) throw (RuntimeException)e;
	    throw new IOException(ex);
	}	
	return in;
    }
    
    // So developers can see this class in stack traces.
    @Override
    public Object invoke(Object proxy, Method method, Object[] args)
	throws Throwable
    {
        return super.invoke(proxy, method, args);
    }

}
