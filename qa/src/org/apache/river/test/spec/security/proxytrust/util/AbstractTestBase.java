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
package org.apache.river.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// java
import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

// org.apache.river
import org.apache.river.qa.harness.QATestEnvironment;

// net.jini
import org.apache.river.qa.harness.Test;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustExporter;
import net.jini.security.proxytrust.ProxyTrustVerifier;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.Integrity;
import net.jini.loader.ClassLoading;


/**
 * Base class for proxytrust spec-tests.
 */
public abstract class AbstractTestBase extends QATestEnvironment implements Test {

    /**
     * Holds expected results for 'isTrustedObject' method calls of
     * ProxyTrustVerifier.
     */
    protected ExpResults expResults;

    /* Number of BaseTrustVerifierContext 'isTrustedObject' method calls. */
    private int ctxCallsNum;

    /* Last thrown RemoteException. */
    private RemoteException lastRE;

    /** ProxyTrust.getProxyVerifier method */
    protected static MethodConstraints validMC;

    static {
        try {
            validMC = new BasicMethodConstraints(
                    new BasicMethodConstraints.MethodDesc[] {
                        new BasicMethodConstraints.MethodDesc(
                                "getProxyVerifier",
                                new InvocationConstraints(
                                        new InvocationConstraint[] {
                                            Integrity.YES }, null)) });
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Print arguments specified and then create ProxyTrustExporter with
     * specified arguments.
     *
     * @param mainExporter Main exporter for ProxyTrustExporter
     * @param bootExporter Boot exporter for ProxyTrustExporter
     * @return ProxyTrustExporter
     */
    public ProxyTrustExporter createPTE(Exporter mainExporter,
                                        Exporter bootExporter) {
        logger.fine("Creating ProxyTrustExporter[mainExporter = "
                + mainExporter + ", bootExporter = " + bootExporter + "]");
        return new ProxyTrustExporter(mainExporter, bootExporter);
    }

    /**
     * Print arguments specified and then create ProxyTrustInvocationHandler
     * with specified arguments.
     *
     * @param main the main proxy
     * @param boot the bootstrap proxy
     * @return ProxyTrustInvocationHandler
     */
    public ProxyTrustInvocationHandler createPTIH(RemoteMethodControl main,
                                                  ProxyTrust boot) {
        logger.fine("Creating ProxyTrustInvocationHandler[main = "
                + main + ", boot = " + boot + "]");
        return new ProxyTrustInvocationHandler(main, boot);
    }

    /**
     * Print arguments specified and then call 'invoke' method of
     * ProxyTrustInvocationHandler specified.
     *
     * @param ptih ProxyTrustInvocationHandler
     * @param proxy the proxy object
     * @param method the method being invoked
     * @param args the arguments to the specified method
     * @return result of 'invoke' method call
     * @throws NullPointerException if ptih is null
     * @throws rethrow any exception thrown by invoke method
     */
    public Object ptihInvoke(ProxyTrustInvocationHandler ptih, Object proxy,
            Method method, Object[] args) throws Exception {
        if (ptih == null) {
            throw new NullPointerException(
                    "ProxyTrustInvocationHandler specified is null.");
        }
        logger.fine("Call 'invoke(): Proxy"
                + ProxyTrustUtil.interfacesToString(proxy) + ", Method: "
                + method + ", Args: " + ProxyTrustUtil.arrayToString(args)
                + "'.");
        try {
            return ptih.invoke(proxy, method, args);
        } catch (Throwable t) {
            if (t instanceof Exception) {
                throw ((Exception) t);
            } else {
                throw new Error(t);
            }
        }
    }

    /**
     * Print arguments specified and then call 'checkTrustEquivalence' method of
     * ProxyTrustInvocationHandler specified.
     *
     * @param ptih ProxyTrustInvocationHandler
     * @param obj parameter to 'checkTrustEquivalence' method
     * @return result of 'checkTrustEquivalence' method call
     * @throws NullPointerException if ptih is null
     */
    public boolean ptihCheckTrustEquivalence(ProxyTrustInvocationHandler ptih,
            Object obj) {
        if (ptih == null) {
            throw new NullPointerException(
                    "ProxyTrustInvocationHandler specified is null.");
        }
        logger.fine("Call 'checkTrustEquivalence(" + obj + ")' method of "
                + ptih + ".");
        return ptih.checkTrustEquivalence(obj);
    }

    /**
     * Print arguments specified and then call 'equals' method of
     * ProxyTrustInvocationHandler specified.
     *
     * @param ptih ProxyTrustInvocationHandler
     * @param obj parameter to 'equals' method
     * @return result of 'equals' method call
     * @throws NullPointerException if ptih is null
     */
    public boolean ptihEquals(ProxyTrustInvocationHandler ptih, Object obj) {
        if (ptih == null) {
            throw new NullPointerException(
                    "ProxyTrustInvocationHandler specified is null.");
        }
        logger.fine("Call 'equals(" + obj + ")' method of " + ptih + ".");
        return ptih.equals(obj);
    }

    /**
     * Create proxy implementing  RemoteMethodControl and TrustEquivalence
     * interfaces to be used as ValidMainProxy.
     *
     * @return proxy created
     */
    public RemoteMethodControl createValidMainProxy() {
        return (RemoteMethodControl) ProxyTrustUtil.newProxyInstance(
                new RMCTEImpl());
    }

    /**
     * Create proxy implementing  RemoteMethodControl, ProxyTrust and
     * TrustEquivalence interfaces to be used as ValidBootProxy.
     *
     * @return proxy created
     */
    public ProxyTrust createValidBootProxy() {
        return (ProxyTrust) ProxyTrustUtil.newProxyInstance(new RMCPTTEImpl());
    }

    /**
     * Creates main proxy.
     *
     * @param impl
     * @return proxy created
     */
    public RemoteMethodControl newMainProxy(Object impl) {
        TestClassLoader cl = new TestClassLoader();
        InvocationHandler ih = new InvHandler(impl);
        return (RemoteMethodControl) ProxyTrustUtil.newProxyInstance(impl, ih,
                cl);
    }

    /**
     * Creates main proxy in RMI child loader.
     *
     * @param impl
     * @return proxy created
     */
    public RemoteMethodControl newRMIMainProxy(Object impl) {
        String jarURL = 
	    "file:" +
	    (getConfig().getStringConfigVal("qa.home", null) +
	     "/lib").replace(File.separatorChar, '/') +
	    "/qa1.jar";
        InvocationHandler ih = new InvHandler(impl);
	try {
	    return (RemoteMethodControl)
		ProxyTrustUtil.newProxyInstance(
			impl, ih, ClassLoading.getClassLoader(jarURL));
	} catch (MalformedURLException e) {
	    throw new AssertionError(e);
	}
    }

    /**
     * Creates boot proxy.
     *
     * @param impl
     * @return proxy created
     */
    public ProxyTrust newBootProxy(Object impl) {
        TestClassLoader cl = new TestClassLoader();
        InvocationHandler ih = new InvHandler(impl);
        return (ProxyTrust) ProxyTrustUtil.newProxyInstance(impl, ih, cl);
    }

    /**
     * Returns true if object specified != null, is instance of Boolean and
     * equal to value expected. In this case success message will be printed.
     * Otherwise false will be returned.
     *
     * @param obj object for checking
     * @param value expected value
     * @return true if obj != null, is instance of Boolean and equal to value
     *          and false otherwise
     */
    public boolean isOk(Object obj, boolean value) {
        if ((obj == null) || !(obj instanceof Boolean)
                || ((Boolean) obj).booleanValue() != value) {
            // FAIL
            return false;
        }

        // PASS
        logger.fine("'invoke' method of constructed "
                + "ProxyTrustInvocationHandler returned " + value
                + " as expected.");
        return true;
    }

    /**
     * Print arguments specified, fills array of expected calls and then call
     * 'isTrustedObject' method of ProxyTrustVerifier.
     *
     * @param ptv tested ProxyTrustVerifier
     * @param obj parameter to 'isTrustedObject' method
     * @param ctx context-parameter to 'isTrustedObject' method
     * @return result of 'isTrustedObject' method call
     * @throws Exception rethrow any exception thrown by 'isTrustedObject'
     *         method call
     * @throws NullPointerException if ptv is null
     * @throws IllegalArgumentException if ctx is not null and is not an
     *         instance of BaseTrustVerifierContext
     */
    public boolean ptvIsTrustedObject(ProxyTrustVerifier ptv, Object obj,
            TrustVerifier.Context ctx) throws RemoteException {
        if (ptv == null) {
            throw new NullPointerException("ProxyTrustVerifier can't be null.");
        }

        if (ctx != null && !(ctx instanceof BaseTrustVerifierContext)) {
            throw new IllegalArgumentException(
                    "ctx is not an instance of BaseTrustVerifierContext.");
        }
        logger.fine("Call 'isTrustedObject(Object = [" + obj + "], Context = ["
                + ctx + "])' method of ProxyTrustVerifier.");

        if (obj != null && ctx != null) {
            expResults = new ExpResults(obj, ctx);
	    expResults.objs = getExpArray(obj, ctx);
	    BaseIsTrustedObjectClass.initClassesArray();
        }
        return ptv.isTrustedObject(obj, ctx);
    }

    /**
     * Returns array of expected objects for checking results of testing.
     * Sets variables for expected result to appropriate values.
     *
     * @param obj Object for 'isTrustedObject' method
     * @param ctx Context for 'isTrustedObject' method
     * @return array of expected objects
     * @throws IllegalArgumentException if ctx is not an instance of
     *         BaseTrustVerifierContext
     */
    private BaseIsTrustedObjectClass[] getExpArray(Object obj,
            TrustVerifier.Context ctx) {
        if (!(ctx instanceof BaseTrustVerifierContext)) {
            throw new IllegalArgumentException(
                    "ctx is not an instance of BaseTrustVerifierContext.");
        }
        ctxCallsNum = 0;
        List list = new ArrayList();
        if (((BaseTrustVerifierContext) ctx).containsValidMC()) {
	    addExpColl(list, obj, ctx);
	}
        if (list.isEmpty() || expResults.res == null) {
            if (expResults.exList.size() > 0) {
                expResults.res = expResults.exList.get(
                        expResults.exList.size() - 1);
            } else {
                expResults.res = new Boolean(false);
            }
        }

        return (BaseIsTrustedObjectClass []) list.toArray(
                new BaseIsTrustedObjectClass[list.size()]);
    }

    /*
     * Adds to list of expected objects.
     * Sets variables for expected result to appropriate values.
     */
    private void addExpColl(List list, Object obj, TrustVerifier.Context ctx) {
        if (!(obj instanceof ProxyTrust) && Proxy.isProxyClass(obj.getClass()))
	{
	    obj = Proxy.getInvocationHandler(obj);
	    if (!Proxy.isProxyClass(obj.getClass())) {
		addExpColl(list, obj, ctx);
	    }
	} else if (obj instanceof NonProxyObjectThrowingRE) {
            // must be called 'setException' method of TrustIterator
            int size = ((NonProxyObjectThrowingRE) obj).getObjArray().length;
            TrustIteratorThrowingRE ti = (TrustIteratorThrowingRE)
                    ((NonProxyObjectThrowingRE) obj).getProxyTrustIterator();

            for (int i = 0; i < size; ++i) {
                list.add(ti);
                expResults.exList.add(ti.getException());
            }
        } else if (obj instanceof ValidNonProxyObject) {
            // must be called 'getProxyTrustIterator' method of obj
            list.add(obj);
            Object[] objs = ((ValidNonProxyObject) obj).getObjArray();

            for (int i = 0; i < objs.length; ++i) {
                lastRE = null;

		addExpColl(list, objs[i], ctx);

		if (lastRE != null) {
		    // must be called 'setException' method of TrustIterator
		    list.add(((ValidNonProxyObject)
			      obj).getProxyTrustIterator());
		}
                if (expResults.res != null) {
		    return;
		}
            }
            lastRE = null;
        } else if (obj instanceof BaseProxyTrust) {
            // must be called 'isTrustedObject' method of ctx
            list.add(ctx);
            expResults.btvcObjs.add(obj);
	    addExpColl2(list, obj, ctx);
	} else if (obj instanceof ProxyTrust &&
		   obj instanceof RemoteMethodControl &&
		   Proxy.isProxyClass(obj.getClass()))
	{
	    Object ih = Proxy.getInvocationHandler(obj);
	    if (!list.isEmpty()) {
		list.add(ctx);
		expResults.btvcObjs.add(obj);
		addExpColl2(list, ((InvHandler) ih).obj, ctx);
	    }
	    if (proper(obj.getClass())) {
		list.add(ctx);
		expResults.btvcObjs.add(new NewProxy(obj));
		addExpColl2(list, ((InvHandler) ih).obj, ctx);
	    }
        }
    }

    /*
     * Returns true if c's loader is a proper RMI child of its parent.
     */
    private static boolean proper(Class c) {
	ClassLoader cl = c.getClassLoader();
	if (cl == null) {
	    return false;
	}
	String cb = ClassLoading.getClassAnnotation(c);
	ClassLoader pl = cl.getParent();
	Thread t = Thread.currentThread();
	ClassLoader ccl = t.getContextClassLoader();
	try {
	    t.setContextClassLoader(pl);
	    return cl == ClassLoading.getClassLoader(cb);
	} catch (MalformedURLException e) {
	    return false;
	} finally {
	    t.setContextClassLoader(ccl);
	}
    }

    /*
     * Adds to collection of expected objects.
     * Sets variables for expected result to appropriate values.
     */
    private void addExpColl2(List list, Object obj, TrustVerifier.Context ctx)
    {
	if (ctx instanceof ThrowingRE) {
	    // RemoteException must be thrown
	    lastRE = ((ThrowingRE) ctx).getException();
	    expResults.exList.add(lastRE);
	    return;
	} else if (ctx instanceof TrustVerifierContext &&
		   (++ctxCallsNum <= ((TrustVerifierContext) ctx).limit))
	{
	    return;
	}
	// must be called 'getProxyVerifier' method of obj
	list.add(obj);
	TrustVerifier tv = null;

	if (!(obj instanceof ProxyTrustThrowingRE1)) {
	    try {
		tv = ((BaseProxyTrust) obj).getProxyVerifier();
	    } catch (RemoteException re) {
		throw new Error(re);
	    }
	}

	if (tv != null) {
	    // must be called 'isTrustedObject' method of trust verifier
	    list.add(tv);
	}

	if (obj instanceof TrueProxyTrust) {
	    expResults.res = new Boolean(true);
	} else if (obj instanceof ProxyTrustThrowingRE1) {
	    // RemoteException must be thrown
	    lastRE = ((ThrowingRE) obj).getException();
	    expResults.exList.add(lastRE);
	} else if (obj instanceof ProxyTrustThrowingRE2) {
	    // RemoteException must be thrown
	    expResults.exList.add(((ThrowingRE) tv).getException());
	} else {
	    expResults.res = new Boolean(false);
	}
    }

    /**
     * Checks expected result with actual one. Returns non-null string
     * indicating first error or null if everything was as expected.
     *
     * @param res result of 'isTrustedObject' method call (could be exception)
     * @return non-null string indicating first error or null if everything was
     *         as expected
     */
    public String checkResult(Object res) {
        int index = 0;
        int exIndex = 0;
        BaseIsTrustedObjectClass[] actArray =
                BaseIsTrustedObjectClass.getClassesArray();
        int len = 0;

        if (expResults.objs == null || expResults.objs.length == 0) {
            if (actArray != null && actArray.length != 0) {
                // FAIL
                return "'isTrustedObject' method of ProxyTrustVerifier does "
                        + "not satisfy specification. Expected: no "
                        + "intermediate checked methods calls. Got: '"
                        + actArray[0].getMethodName()
                        + "' method call of " + actArray[0] + ".";
            }
        } else {
            if (actArray == null || actArray.length == 0) {
                // FAIL
                return "'isTrustedObject' method of ProxyTrustVerifier does "
                        + "not satisfy specification. Expected: '"
                        + expResults.objs[0].getMethodName()
                        + "' method call of " + expResults.objs[0]
                        + ". Got: no intermediate checked method calls.";
            }
            len = Math.min(expResults.objs.length, actArray.length);
        }

        for (int i = 0; i < len; ++i) {
            if (expResults.objs[i] != actArray[i]) {
                // FAIL
                return "'isTrustedObject' method of ProxyTrustVerifier does "
                        + "not satisfy specification. Expected: '"
                        + expResults.objs[i].getMethodName()
                        + "' method call of " + expResults.objs[i] + ". Got: '"
                        + actArray[i].getMethodName() + "' method call of "
                        + actArray[i] + ".";
            }

            // PASS
            logger.fine("Check " + (i + 1) + ": '"
                    + expResults.objs[i].getMethodName() + "' method of "
                    + actArray[i] + " was called as expected.");

            if (expResults.objs[i] instanceof ThrowingRE) {
                ++exIndex;
            }

            // check that parameters for checked method are ok
            if (expResults.objs[i] instanceof BaseProxyTrust) {
                MethodConstraints mc = ((BaseProxyTrust)
                        expResults.objs[i]).getConstraints();

                if (expResults.mc != mc) {
                    // FAIL
                    return "'" + expResults.objs[i].getMethodName()
                            + "' method was invoked with: " + mc
                            + " MethodConstraints while " + expResults.mc
                            + " was expected.";
                }
            } else if (expResults.objs[i] instanceof BaseTrustVerifierContext) {
                Object actObj = ((BaseTrustVerifierContext)
                        expResults.objs[i]).getObjsList().get(index);
                Object expObj = expResults.btvcObjs.get(index);
                ++index;

                if (!(actObj == expObj ||
		      (expObj instanceof NewProxy &&
		       ((NewProxy) expObj).equal(actObj))))
		{
                    // FAIL
                    return "'" + expResults.objs[i].getMethodName()
                            + "' method was invoked with: " + actObj
                            + " parameter while " + expObj + " was expected.";
                }
            } else if (expResults.objs[i] instanceof BaseTrustVerifier) {
                Object obj = ((BaseTrustVerifier) expResults.objs[i]).getObj();
                TrustVerifier.Context ctx = ((BaseTrustVerifier)
                        expResults.objs[i]).getCtx();

                if (expResults.btvObj != obj || expResults.btvCtx != ctx) {
                    // FAIL
                    return "'" + expResults.objs[i].getMethodName()
                            + "' method was invoked with: Object = " + obj
                            + ", Context = " + ctx + ", while: Object = "
                            + expResults.btvObj + ", Context = "
                            + expResults.btvCtx + " was expected.";
                }
            } else if ((expResults.objs[i] instanceof TestTrustIterator)
                    && !(expResults.objs[i] instanceof ThrowingRE)) {
                RemoteException actRE = ((TestTrustIterator)
                        expResults.objs[i]).nextException();
                RemoteException expRE = (RemoteException)
                        expResults.exList.get(exIndex - 1);

                if (actRE != expRE) {
                    // FAIL
                    return "'" + expResults.objs[i].getMethodName()
                            + "' method was invoked with " + actRE
                            + " exception whil " + expRE + " was expected.";
                }
            }

            // PASS
            logger.fine("Parameters passed to this method were as expected.");
        }

        if (expResults.objs != null && actArray != null
                && expResults.objs.length != actArray.length) {
            // FAIL
            if (expResults.objs.length > actArray.length) {
                return "'isTrustedObject' method of ProxyTrustVerifier does "
                        + "not satisfy specification. Expected: '"
                        + expResults.objs[len].getMethodName()
                        + "' method call of " + expResults.objs[len]
                        + ". Got: no more checked method calls.";
            } else {
                return "'isTrustedObject' method of ProxyTrustVerifier does "
                        + "not satisfy specification. Expected: no "
                        + "more checked methods calls. Got: '"
                        + actArray[len].getMethodName() + "' method call of "
                        + actArray[len] + ".";
            }
        }

        if (res instanceof Boolean) {
            // Boolean result was expected
            if (((Boolean) expResults.res).booleanValue() != ((Boolean)
                    res).booleanValue()) {
                // FAIL
                return "'isTrustedObject' method of ProxyTrustVerifier "
                        + "returned " + res + " while " + expResults.res
                        + " was expected.";
            }

            // PASS
            logger.fine("'isTrustedObject' method of ProxyTrustVerifier "
                    + "returned " + res + " as expected.");
            return null;
        } else {
            // RemoteException was expected
            if (expResults.res != res) {
                // FAIL
                return "'isTrustedObject' method of ProxyTrustVerifier "
                    + "threw " + res + " while " + expResults.res
                    + " was expected.";
            }

            // PASS
            logger.fine("'isTrustedObject' method of ProxyTrustVerifier "
                    + "threw " + res + " as expected.");
            return null;
        }
    }


    /**
     * Class holding various information expected from 'isTrustedObject' method
     * calls.
     */
    class ExpResults {
        /** Array of expected objects whose checked method should be called. */
        public BaseIsTrustedObjectClass[] objs;

        /** Expected result of 'isTrustedObject' method call */
        public Object res;

        /**
         * Expected MethodConstraints passed to 'getProxyVerifier' method
         * of BaseProxyTrust classes.
         */
        public MethodConstraints mc;

        /**
         * Expected list of objects which will be passed to multiple
         * 'isTrustedObject' method calls of BaseTrustVerifierContext classes.
         */
        public ArrayList btvcObjs;

        /**
         * Expected list of RemoteExceptions which should be produced.
         */
        public ArrayList exList;

        /**
         * Expected object passed 'isTrustedObject' method of
         * BaseTrustVerifier classes.
         */
        public Object btvObj;

        /**
         * Expected context passed 'isTrustedObject' method of
         * BaseTrustVerifier classes.
         */
        public TrustVerifier.Context btvCtx;

        /**
         * Constructor initializing variables.
         *
         * @param expBtvObj expected object passed 'isTrustedObject' method of
         *                  BaseTrustVerifier classes
         * @param expBtvCtx expected context passed 'isTrustedObject' method of
         *                  BaseTrustVerifier classes
         */
        public ExpResults(Object expBtvObj, TrustVerifier.Context expBtvCtx) {
            objs = null;
            res = null;
            btvcObjs = new ArrayList();
            exList = new ArrayList();
            btvObj = expBtvObj;
            btvCtx = expBtvCtx;
            mc = ((BaseTrustVerifierContext) expBtvCtx).getValidMC();
        }
    }

    class NewProxy {
	private Object obj;

	NewProxy(Object obj) {
	    this.obj = obj;
	}

	boolean equal(Object obj) {
	    Class c = obj.getClass();
	    return (Proxy.isProxyClass(c) &&
		    c.getClassLoader() == getClass().getClassLoader() &&
		    Proxy.getInvocationHandler(obj) ==
		    Proxy.getInvocationHandler(this.obj) &&
		    obj instanceof ProxyTrust &&
		    obj instanceof RemoteMethodControl &&
		    c.getInterfaces().length == 2);
	}
    }
}
