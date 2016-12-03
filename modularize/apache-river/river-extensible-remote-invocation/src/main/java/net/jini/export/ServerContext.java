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

import org.apache.river.resource.Service;
import java.rmi.server.ServerNotActiveException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import net.jini.io.context.ClientHost;
import net.jini.io.context.ClientSubject;
import net.jini.security.Security;

/**
 * The <code>ServerContext</code> class contains static methods to
 * execute some action within an appropriate server context for the
 * current thread and to obtain the server context for the current thread.
 *
 * <p>For example, an {@link Exporter} implementation may supply context
 * information for incoming calls dispatched to its exported remote objects
 * via the {@link #doWithServerContext ServerContext.doWithServerContext}
 * method.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 * @see net.jini.io.context.ClientHost
 * @see net.jini.io.context.ClientSubject
 **/
public final class ServerContext {

    private static final ThreadLocal state = new ThreadLocal();
    private static ServerContext.Spi[] providers = null; 
    private static final Object LOCK = new Object();

    private static Spi[] getProviders() {
        synchronized (LOCK){
            if (providers != null) return providers;
            
            providers = Security.doPrivileged(new PrivilegedAction<Spi[]>() {

                public Spi[] run() {
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    List<Spi> list = new ArrayList<Spi>(1);
                    Iterator<Spi> i = Service.providers(Spi.class, cl);
                    while (i.hasNext()) {
                        list.add(i.next());
                    }
                    return list.toArray(new Spi[list.size()]);
                }
            });
        
            return providers;
        }
    }

    /**
     * Prevents instantiation.
     */
    private ServerContext() {
    }

    /**
     * Sets the server context for the current thread to the supplied
     * <code>context</code> collection and invokes the <code>run</code> method
     * of the supplied <code>runnable</code> object.  When this method
     * returns, the thread's server context is unset.
     *
     * <p>If a server context is currently set for the current thread,
     * that server context cannot be reset; that is, a server context
     * cannot be overwritten or nested.  If a server context is already
     * set for the current thread, an <code>IllegalStateException</code> is
     * thrown.
     *
     * @param	runnable the action to perform in the server context
     * @param	context the context to set
     *
     * @throws	NullPointerException if <code>context</code> or
     *		<code>runnable</code> is <code>null</code>
     * @throws	IllegalStateException if the context is already
     * 		set for this thread
     * @see #getServerContextElement
     **/
    public static void doWithServerContext(Runnable runnable,
            Collection context) {
        if (context == null) {
            throw new NullPointerException("context cannot be null");
        }
        if (state.get() != null) {
            throw new IllegalStateException(
                    "context is already set for this thread");
        }

        state.set(context);
        try {
            runnable.run();
        } finally {
            state.set(null);
        }
    }

    /**
     * Returns the server context collection for the current thread.  If no
     * server context collection has been explicitly specified via a
     * previous call to {@link #doWithServerContext
     * ServerContext.doWithServerContext}, then an ordered list of
     * providers (obtained as specified below) implementing the {@link
     * ServerContext.Spi} interface is consulted.  {@link
     * ServerContext.Spi#getServerContext} is called on each provider in
     * turn; the first non-<code>null</code> return value is returned by
     * this method.  If no provider is able to supply a server context
     * collection, then a <code>ServerNotActiveException</code> is thrown.
     *
     * <p>The list of server context providers is obtained as follows.  For
     * each resource named
     * <code>META-INF/services/net.jini.export.ServerContext$Spi</code>
     * that is visible to the system class loader, the contents of the
     * resource are parsed as UTF-8 text to produce a list of class names.
     * The resource must contain a list of fully-qualified class names, one per
     * line. Space and tab characters surrounding each name, as well as blank
     * lines, are ignored.  The comment character is <tt>'#'</tt>; all
     * characters on each line starting with the first comment character are
     * ignored. Each class name (that is not a duplicate of any previous class
     * name) is loaded through the system class loader, and the resulting
     * class must be assignable to {@link ServerContext.Spi} and have a public
     * no-argument constructor. The constructor is invoked to create a fallback
     * context provider instance. An implementation is permitted to search for
     * provider instances eagerly (i.e., upon startup), and cache the
     * verifier instances rather than recreating them on every call.
     * 
     * <p>The contents of the collection are determined by the caller of
     * {@link #doWithServerContext ServerContext.doWithServerContext}.  The
     * context information available from a given element of the collection
     * is determined by that element's type.  Examples of types that a
     * given element might implement include {@link ClientHost} and {@link
     * ClientSubject}.
     *
     * <p>The order of the elements in the collection is insignificant.
     * The collection may be empty.
     *
     * <p>The caller of this method cannot assume that the returned
     * collection is modifiable.
     *
     * @return	the server context for the current thread
     * @throws	ServerNotActiveException if no context is set for the
     *		current thread
     **/
    public static Collection getServerContext()
            throws ServerNotActiveException {
        Collection context = (Collection) state.get();
        if (context == null) {
            for (int i = 0, l = getProviders().length; i < l; i++) {
                if ((context = getProviders()[i].getServerContext()) != null) {
                    break;
                }
            }
        }
        if (context == null) {
            throw new ServerNotActiveException("not in remote call");
        }
        return context;
    }

    /**
     * Defines the interface for server context providers, which are
     * consulted by {@link ServerContext#getServerContext} if no server context
     * has been explicitly associated with the current thread via a previous
     * call to {@link ServerContext#doWithServerContext}.
     */
    public interface Spi {

        /**
         * Returns a server context collection for the current thread, or
         * <code>null</code> if provider does not contain context for the
         * current thread.
         * 
         * <p>The context information available from a given element of
         * the collection is determined by that element's type.  The order
         * of the elements is insignificant.  The collection may be empty.
         *
         * <p>The caller of this method cannot assume that the returned
         * collection is modifiable.
         *
         * @return  the server context for the current thread,
         *	    or <code>null</code> if none known
         */
        Collection getServerContext();
    }

    /**
     * Returns the first element in the current server context collection
     * (obtained by calling {@link #getServerContext
     * ServerContext.getServerContext}) that is an instance of the given
     * type <code>type</code>.  If no element in the collection is an
     * instance of the type <code>type</code>, then <code>null</code> is
     * returned.
     *
     * @param	type the type of the element
     * @return 	the first element in the server context collection that is
     *		an instance of the type <code>type</code> or <code>null</code>
     * @throws	ServerNotActiveException if no server context is set for
     *		the current thread
     **/
    public static Object getServerContextElement(Class type)
            throws ServerNotActiveException {
        Collection context = getServerContext();
        Iterator iter = context.iterator();
        while (iter.hasNext()) {
            Object elem = iter.next();
            if (elem != null && type.isAssignableFrom(elem.getClass())) {
                return elem;
            }
        }
        return null;
    }
}
