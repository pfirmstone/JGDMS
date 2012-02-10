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

package org.apache.river.impl.security.dos;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.jini.io.MarshalledInstance;

/**
 * A preliminary experiment into Isolating a Smart Proxy.
 * 
 * I think I'll investigate creating a Permission for additional threads
 * for improved performance and also creating an event model so clients
 * don't need to wait for remote method returns.  I'll create a new interface
 * for this, that can be implemented by services directly too.
 * 
 * Alternative, only maintain this data structure for as long as it takes
 * to verify proxy trust, then return proxy to the client or have the client
 * thread call the smart proxy via the reflection proxy directly.
 * 
 * REMIND: Investigate subclassed return values, this is simple, if the
 * smart proxy is confined to it's own ClassLoader, we just check the class
 * of the object returned isn't from that ClassLoader, unless the smart proxy
 * has a ServiceAPISubclassPermission or something like that.  The reason for 
 * checking that remote code hasn't escaped the IsolatedExecutor, is that we want to
 * ensure it cannot harm the application threads.
 * 
 * @author Peter Firmstone
 */
public class ProxyIsolationHandler implements InvocationHandler {
    private volatile Object smartProxy;
    private final IsolatedExecutor isolate;
    private final long timeout;
    private final TimeUnit unit;
    private final ClassLoader proxyLoader;
    
    @SuppressWarnings("unchecked")
    public ProxyIsolationHandler(MarshalledInstance proxy, 
			    ClassLoader defaultLoader,
			    boolean verifyCodebaseIntegrity, 
			    ClassLoader verifierLoader,
			    Collection context,
                            long timeout,
                            TimeUnit timeUnit
	    ) throws TimeoutException, InterruptedException, ExecutionException, IsolationException{
        if (defaultLoader == null) throw new IsolationException("A default " +
                "ClassLoader must be defined for effective isolation");
        isolate = new IsolatedExecutor();
	this.timeout = timeout;
        unit = timeUnit;
	UnmarshallProxyTask task = 
		new UnmarshallProxyTask(proxy, defaultLoader,
		verifyCodebaseIntegrity, verifierLoader, context);
        smartProxy = isolate.process(task, timeout, timeUnit);
        proxyLoader = defaultLoader;
    }
    
    private Object taskInvoke(Object proxy, Method method, Object[] args) throws
	    Exception {
	String methodName = method.getName();
	if (method.getDeclaringClass() == Object.class)  {
	    // Handle the Object public methods.
	    if (methodName.equals("hashCode"))  {
		return new Integer(System.identityHashCode(proxy));   
	    } else if (methodName.equals("equals")) {
		return (proxy == args[0] ? Boolean.TRUE : Boolean.FALSE);
	    } else if (methodName.equals("toString")) {
		return proxy.getClass().getName() + '@' + Integer.toHexString(proxy.hashCode());
	    }
	}
	return method.invoke(smartProxy, args);	
    }
    
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
	Callable task = new MethodInvocationTask(this, proxy, method, args);
        Object result = isolate.process(task, timeout, unit);
        CheckReturn cr = new CheckReturn(result, proxyLoader);
        return AccessController.doPrivileged(cr);
    }
    
    private static class UnmarshallProxyTask implements Callable{
	private final MarshalledInstance mi;
	private final ClassLoader defaultLoader;
	private final boolean verifyCodebaseIntegrity;
	private final ClassLoader verifierLoader;
	private final Collection context;
	
	UnmarshallProxyTask(MarshalledInstance proxy, 
		ClassLoader defaultLoader,
		boolean verifyCodebaseIntegrity, 
		ClassLoader verifierLoader,
		Collection context)
	{
	    mi = proxy;
	    this.defaultLoader = defaultLoader;
	    this.verifyCodebaseIntegrity = verifyCodebaseIntegrity;
	    this.verifierLoader = verifierLoader;
	    this.context = context;
	}	

	public Object call() throws Exception {
	    return mi.get(defaultLoader, verifyCodebaseIntegrity, 
		    verifierLoader, context);
	}
	
    }
    
    private static class MethodInvocationTask implements Callable {
	private final ProxyIsolationHandler smartProxy;
	private final Object proxy;
	private final Method method;
	private final Object[] args;
	MethodInvocationTask(ProxyIsolationHandler target, Object proxy, Method method, Object[] args){
	    smartProxy = target;
	    this.proxy = proxy;
	    this.method = method;
	    this.args = args;	    
	}
	public Object call() throws Exception {
	    return smartProxy.taskInvoke(proxy, method, args);
	}
	    
    }
    
    private static class CheckReturn implements PrivilegedExceptionAction{
        private final Object checked;
        private final ClassLoader loader;
        CheckReturn (Object check, ClassLoader loader){
            checked = check;
            this.loader = loader;
        }
        public Object run() throws Exception {
            Class clazz = checked.getClass();
            Class[] classes = clazz.getDeclaredClasses();
            Set<ClassLoader> loaders = new HashSet<ClassLoader>();
            loaders.add(clazz.getClassLoader());
            int l = classes.length;
            for (int i = 0; i < l; i++){
                loaders.add(classes[i].getClassLoader());
            }
            if (loaders.contains(loader)) throw new IsolationException("Attempt" +
                    " to return isolated code from isolation classloader");
            return checked;
        }
        
    }

}
