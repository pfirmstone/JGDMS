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

package net.jini.loader;

//import java.security.AccessController;
//import java.security.PrivilegedAction;
//import java.util.Collection;
//import java.util.List;
//import org.apache.river.concurrent.RC;
//import org.apache.river.concurrent.Ref;
//import org.apache.river.concurrent.Referrer;
//import java.util.concurrent.Callable;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Future;
//import java.util.concurrent.FutureTask;
//import java.util.concurrent.LinkedBlockingQueue;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.TimeoutException;
//import org.apache.river.thread.NamedThreadFactory;

/**
 * LoadClass delegates to @link {Class#forName(String, boolean, ClassLoader)},
 * calls to each ClassLoader are thread confined.
 * 
 * @author peter
 * @deprecated
 */
@Deprecated
public class LoadClass {
    
    private LoadClass() {throw new AssertionError();}
    /**
     * loaderMap contains a list of single threaded ExecutorService's for
     * each ClassLoader, used for loading classes and proxies to avoid
     * ClassLoader lock contention. An Entry is removed if the ClassLoader
     * becomes weakly reachable, or the ExecutorService hasn't been used
     * recently.
     */
//    private static final ConcurrentMap<ClassLoader, ExecutorService> loaderMap 
//            = RC.concurrentMap(
//                    new ConcurrentHashMap<Referrer<ClassLoader>,
//                    Referrer<ExecutorService>>(),
//                    Ref.WEAK_IDENTITY,
//                    Ref.TIME,
//                    10000L,
//                    10000L
//            );

    /**
     * Returns the {@code Class} object associated with the class or
     * interface with the given string name, using the given class loader.
     *
     * This method calls {@link Class#forName(String,boolean,ClassLoader)},
     * from a Thread dedicated for each
     * ClassLoader, avoiding contention for ClassLoader locks by thread
     * confinement.  This provides a significant scalability benefit for
     * JERI, without needing to resort to parallel ClassLoader locks, which
     * isn't part of the Java specification.
     *
     * If loader is null, thread confinement is not used.
     *
     * @param name       fully qualified name of the desired class
     * @param initialize whether the class must be initialized
     * @param loader     class loader from which the class must be loaded
     * @return           class object representing the desired class
     *
     * @exception LinkageError if the linkage fails
     * @exception ExceptionInInitializerError if the initialization provoked
     *            by this method fails
     * @exception ClassNotFoundException if the class cannot be located by
     *            the specified class loader, within three minutes.
     * @see Class
     * @since 3.0
     * @deprecated 
     */
    @Deprecated
    public static Class forName(String name, boolean initialize, ClassLoader loader) 
            throws ClassNotFoundException 
    {
        return Class.forName(name, initialize, loader);
//        if (loader == null) return Class.forName(name, initialize, null);
//        if (loader.toString().startsWith(
//                "javax.management.remote.rmi.RMIConnectionImpl")) 
//        {
//            return Class.forName(name, initialize, loader);
//        }
//        ExecutorService exec = loaderMap.get(loader);
//        if (exec == null) {
//            exec = new AutoCloseableExecutor(loader.toString());
//            ExecutorService existed = loaderMap.putIfAbsent(loader, exec);
//            if (existed != null) {
//                exec = existed;
//            }
//        }
//        FutureTask<Class> future = 
//                new FutureTask(new GetClassTask(name, initialize, loader));
//        exec.submit(future);
//        try {
//            return future.get(3L, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//	    Throwable fillInStackTrace = e.fillInStackTrace();
//            throw new ClassNotFoundException(
//                    "Interrupted, Unable to find Class: " + name, fillInStackTrace);
//        } catch (ExecutionException e) {
//            Throwable t = e.getCause();
//            if (t instanceof LinkageError) {
//                throw (LinkageError) t;
//            }
//            if (t instanceof ExceptionInInitializerError) {
//                throw (ExceptionInInitializerError) t;
//            }
//            if (t instanceof SecurityException) {
//                throw (SecurityException) t;
//            }
//            if (t instanceof ClassNotFoundException) {
//                throw (ClassNotFoundException) t;
//            }
//            if (t instanceof NullPointerException) {
//                throw (NullPointerException) t;
//            }
//	    if (t instanceof RuntimeException){
//		throw (RuntimeException) t;
//	    }
//	    if (t instanceof Error){
//		throw (Error) t;
//	    }
//            throw new ClassNotFoundException(
//                    "Unable to find Class:" + name, t);
//        } catch (TimeoutException ex) {
//	    throw new ClassNotFoundException(
//		"Timeout after waiting three minutes to load class: " + name,
//		ex
//	    );
//	}
    }

//    private static class GetClassTask implements Callable<Class> {
//
//        private static final ClassLoader SYSTEM_LOADER =
//                ClassLoader.getSystemClassLoader();
//        private final String name;
//        private final boolean initialize;
//        private final ClassLoader loader;
//
//        private GetClassTask(String name, boolean initialize, ClassLoader loader) {
//            this.name = name;
//            this.initialize = initialize;
//            this.loader = loader;
//        }
//
//        @Override
//        public Class call() throws ClassNotFoundException {
//            try {
//                return Class.forName(name, initialize, loader);
//            } finally {
//                /**
//                 * See jtreg sun bug ID:6304035
//                 * This ensures that a thread doesn't unnecessarily hold 
//                 * a strong reference to a ClassLoader, thus preventing
//                 * it from being garbage collected.
//                 */
//                AccessController.doPrivileged(new PrivilegedAction(){
//                    public Object run() {
//                        Thread.currentThread().setContextClassLoader(SYSTEM_LOADER);
//                        return null;
//                    }
//                });
//                
//            }
//        }
//
//    }
//
//    private static class AutoCloseableExecutor implements ExecutorService, AutoCloseable {
//
//	private final ExecutorService decorated;
//	
//	AutoCloseableExecutor(String loaderName){
//	    decorated = new ThreadPoolExecutor(
//                    1,
//                    1,
//                    0,
//                    TimeUnit.SECONDS,
//                    new LinkedBlockingQueue(),
//                    new NamedThreadFactory(loaderName, false),
//                    new ThreadPoolExecutor.CallerRunsPolicy());
//}
//	
//	@Override
//	public void shutdown() {
//	    decorated.shutdown();
//	}
//
//	@Override
//	public List<Runnable> shutdownNow() {
//	    return decorated.shutdownNow();
//	}
//
//	@Override
//	public boolean isShutdown() {
//	    return decorated.isShutdown();
//	}
//
//	@Override
//	public boolean isTerminated() {
//	    return decorated.isTerminated();
//	}
//
//	@Override
//	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
//	    return decorated.awaitTermination(timeout, unit);
//	}
//
//	@Override
//	public <T> Future<T> submit(Callable<T> task) {
//	    return decorated.submit(task);
//	}
//
//	@Override
//	public <T> Future<T> submit(Runnable task, T result) {
//	    return decorated.submit(task, result);
//	}
//
//	@Override
//	public Future<?> submit(Runnable task) {
//	    return decorated.submit(task);
//	}
//
//	@Override
//	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
//	    return decorated.invokeAll(tasks);
//	}
//
//	@Override
//	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
//	    return decorated.invokeAll(tasks, timeout, unit);
//	}
//
//	@Override
//	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
//	    return decorated.invokeAny(tasks);
//	}
//
//	@Override
//	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
//	    return decorated.invokeAny(tasks, timeout, unit);
//	}
//
//	@Override
//	public void execute(Runnable command) {
//	    decorated.execute(command);
//	}
//
//	@Override
//	public void close() throws SecurityException {
//	    decorated.shutdown();
//	}
//	
//    }

}
