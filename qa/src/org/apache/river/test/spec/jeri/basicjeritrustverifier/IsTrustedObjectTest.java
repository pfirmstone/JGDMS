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
/**
 * Purpose: The purpose of this test verify the operation of
 * <code>BasicJeriTrustVerifier.isTrustedObject</code>.
 *
 * Use Case: Verifying trust on objects generated by BasicJeriExporter.
 *
 * Test Design:
 * 1. Export a remote object using a BasicJeriExporter to obtain the
 *    proxy, and then export the remote object.
 * 2. Create a BasicJeriTrustVerifier object and verify that the proxy
 *    returned from step 1 is trusted.
 * 3. Create a dynamic proxy class in the thread's context class loader
 *    that implements RemoteMethodControl but does not use
 *    BasicInvocationHandler as the invocation handler.
 * 4. Verify that BasicJeriTrustVerifier does not trust the object.
 * 5. Create a class loader that is a sibling of the thread's context
 *    class loader with a codebase that provides integrity.
 * 6. Verify that BasicJeriTrustVerifier does not trust a dynamic proxy
 *    if the proxy class is created in the class loader created in step 5.
 * 7. Verify that passing null parameters results in NullPointerException
 * 8. Verify that a remote exception in the TrustVerifier.ctx is propagated.
 * 9. Create a child class loader of the thread's context class loader
 *    with a codebase that provides integrity.
 * 10.Verify that BasicJeriTrustVerifier does not trust a dynamic proxy if
 *    the proxy class is created in the class loader created in step 9.
 *
 * Additional utilities:
 * 1. Test TrustVerifier.Context implementation
 * 2. Test service interface and implementation
 */
package org.apache.river.test.spec.jeri.basicjeritrustverifier;

import java.util.logging.Level;

//jeri imports
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.BasicJeriTrustVerifier;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

//utility classes
import org.apache.river.test.spec.jeri.basicjeritrustverifier
    .util.AbstractTrustVerifierTest;
import org.apache.river.test.spec.jeri.basicjeritrustverifier
    .util.TestTrustVerifierCtx;
import org.apache.river.test.spec.jeri.basicjeritrustverifier.util.TestServiceImpl;

//harness imports
import org.apache.river.qa.harness.TestException;

//java.io
import java.io.File;

//java.rmi
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoader;

//java.lang
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;

//java.net
import java.net.URL;
import java.net.URLClassLoader;
import net.jini.loader.ClassLoading;

public class IsTrustedObjectTest extends AbstractTrustVerifierTest {

    //inherit javadoc
    public void run() throws Exception {
        //Verify that an object exported by BasicJeriExporter is trusted
        BasicJeriTrustVerifier verifier = new BasicJeriTrustVerifier();
        Integer port = new Integer(getStringValue("listenPort"));
        BasicJeriExporter exporter = new BasicJeriExporter(
            TcpServerEndpoint.getInstance(port.intValue()),
	    new BasicILFactory());
        Remote stub = exporter.export(new TestServiceImpl());
	exporter.unexport(true);
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        TestTrustVerifierCtx ctx = new TestTrustVerifierCtx(
            true, loader);
        if (!verifier.isTrustedObject(stub, ctx)){
            throw new TestException("Returned false for an"
               + " object that should be trusted: " + stub);
        }
        //Verify that a proxy object without BasicInvocationHandler is
        //not trusted
        Object proxy = Proxy.newProxyInstance(loader,new Class[]{},
            new InvocationHandler() {
                public Object invoke(Object proxy, Method method,
                    Object[] args) {
                    return null;
                }
            });
        if (verifier.isTrustedObject(proxy,ctx)){
            throw new TestException("Verifier trusts an "
                + " untrusted object.");
        }
        //Verify that using a classloader outside the object's classloader
        //hierearchy causes the object to not be trusted.
        String jarPath = sysConfig.getStringConfigVal(
            "qa.home",null);
	jarPath += "/lib";
        jarPath = jarPath.replace(File.separatorChar, '/');
        String jarURL = "file:" + jarPath + "/qa1.jar";
        URLClassLoader sibling = new URLClassLoader(new URL[]{
            new URL(jarURL)}, loader.getParent());
	Object proxy2 =
	    Proxy.newProxyInstance(sibling, new Class[0],
				   Proxy.getInvocationHandler(stub));
        if (verifier.isTrustedObject(proxy2,ctx)){
            throw new TestException("Verifier trusts a "
                + "proxy outside the class loader hierarchy.");
        }
        //Verify Exceptions
        boolean exceptionThrown = false;
        try {
            verifier.isTrustedObject(stub, null);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Null pointer exception"
                + " not thrown with null context");
        }
        exceptionThrown = false;
        try {
            verifier.isTrustedObject(null, ctx);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Null pointer exception"
                + " not thrown with null object");
        }
        exceptionThrown = false;
	TestTrustVerifierCtx ctx2 =
	    new TestTrustVerifierCtx(true,loader,new RemoteException());
        try {
            verifier.isTrustedObject(stub,ctx2);
        } catch (RemoteException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("RemoteException thrown in"
                + " context was not propagated");
        }
        Object proxy3 = Proxy.newProxyInstance(
	    ClassLoading.getClassLoader(jarURL),
	    stub.getClass().getInterfaces(),
	    Proxy.getInvocationHandler(stub));
        if (verifier.isTrustedObject(proxy3,ctx)){
            throw new TestException("Verifier trusts an "
                + " untrusted object.");
        }
    }

}
