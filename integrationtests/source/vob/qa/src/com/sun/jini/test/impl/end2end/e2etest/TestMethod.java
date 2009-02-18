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

package com.sun.jini.test.impl.end2end.e2etest;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import net.jini.core.constraint.*; // XXX make this explicit
import java.util.ArrayList;

/**
 * A class which encapsulates java.lang.reflect.Method to provide
 * support for utility methods.
 */
class TestMethod {

    Method method;

    private static final SubjectProvider subjectProvider =
        ProviderManager.getSubjectProvider();

    /**
     * A factory method for transforming an array of <code>Method</code>
     * objects into an array of <code>TestMethod</code> objects.
     *
     * @param methods the array of <code>Method</code> object to transform
     * @return the corresponding array of <code>TestMethod</code> objects.
     */
    static TestMethod[] getDeclaredMethods(Class clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        TestMethod[] testMethods = new TestMethod[methods.length];
        for (int i = methods.length; --i >= 0; ) {
            testMethods[i] = new TestMethod(methods[i]);
        }
        return testMethods;
    }

    /**
     * Constructs a <code>TestMethod</code>
     *
     * @param method the <code>Method</code> encapsulated by this
     * <code>TestMethod</code>
     */
    TestMethod(Method method) {
        this.method = method;
    }

    /**
     * Get the name of the method
     *
     * @return the name of the encapsulated <code>Method</code>
     */
    public String getName() {
        return method.getName();
    }

    /**
     * Get the parameters types of the method
     *
     * @return the parameter types of the encapsulated <code>Method</code>
     */
    public Class[] getParameterTypes() {
        return method.getParameterTypes();
    }

    /**
     * An accessor for the contained method.
     *
     * @return the <code>Method</code> represented by this
     * <code>TestMethod</code>
     */
    Method getMethod() {
        return method;
    }

    /**
     * Parse the name of this <code>TestMethod</code> using the
     * naming convention used to construct methods for the
     * <code>ConstraintsInterface</code>,
     * and return a <code>InvocationConstraints</code> object containing
     * the constraints implied by the method name.
     *
     * @return the parsed <code>InvocationConstraints</code>
     */
    InvocationConstraints parseConstraints() {
        ArrayList constraintsList = new ArrayList();
        String name = method.getName();
        if (name.indexOf("Noauth") >= 0) {
            if (!ProviderManager.isKerberosProvider()){
                constraintsList.add(ServerAuthentication.NO);
            }
        }
        if (name.indexOf("Auth") >= 0) {
            constraintsList.add(ClientAuthentication.YES);
        }
        if (name.indexOf("Noconf") >= 0) {
            constraintsList.add(Confidentiality.NO);
        }
        if (name.indexOf("Conf") >= 0) {
            constraintsList.add(Confidentiality.YES);
        }
        if (name.indexOf("Integ") >= 0) {
            constraintsList.add(Integrity.YES);
        }
        if (name.indexOf("CMinP") >= 0) {
            constraintsList.add(subjectProvider.getClientMinPrincipal());
        }
        if (name.indexOf("CMinTp") >= 0) {
            constraintsList.add(subjectProvider.getClientMinPrincipalType());
        }
        if (name.indexOf("Alt1") >= 0) {
            constraintsList.add(subjectProvider.getConstraintAlternatives1());
        }
        if (name.indexOf("Alt2") >= 0) {
            constraintsList.add(subjectProvider.getConstraintAlternatives2());
        }
        if (name.equals("multi")) {
            constraintsList.add(ClientAuthentication.YES);
            constraintsList.add(Confidentiality.YES);
            constraintsList.add(Integrity.YES);
        }
        InvocationConstraints parsedConstraints =
            new InvocationConstraints(constraintsList, null);
        return parsedConstraints;
    }

    /**
     * Return a partial signature string for this <code>TestMethod</code>
     *
     * @return a String consisting of the return type, method name, and
     *         parameter types. The type tokens are unqualified class names.
     */
    String getSignature() {
        String retString = "";
        if (method != null) {
            Class retType = method.getReturnType();
            retString = retType.getName();
            retString = retString.substring(retString.lastIndexOf(".")+1);
            Class[] ptypes = method.getParameterTypes();
            String argString = "(";
            for (int i=0; i<ptypes.length; i++) {
                if (i > 0)argString += ",";
                int index = ptypes[i].getName().lastIndexOf(".");
                argString += ptypes[i].getName().substring(index+1);
            }
            argString += ")";
            retString = retString + " " + method.getName() + argString;
        }
        return retString;
    }

    /**
     * Determine whether this method imposes the Alt1 constraints
     *
     * @return <code>true</code> if the constraints imposed on this
     * method include requirements for Alt1 constraints
     */
    boolean requiresAlt1() {
        return method.getName().indexOf("Alt1") >= 0;
    }

    /**
     * Determine whether this method imposes the Alt2 constraints
     *
     * @return <code>true</code> if the constraints imposed on this
     * method include requirements for Alt2 constraints
     */
    boolean requiresAlt2() {
        return method.getName().indexOf("Alt2") >= 0;
    }

    public boolean equals(Object obj) {
        return method.equals(obj);
    }

    public Class getDeclaringClass() {
        return method.getDeclaringClass();
    }

    public Class[] getExceptionTypes() {
        return method.getExceptionTypes();
    }

    public int getModifiers() {
        return method.getModifiers();
    }

    public Class getReturnType() {
        return method.getReturnType();
    }

    public int hashCode() {
        return method.hashCode();
    }

    public Object invoke(Object obj, Object[] args)
        throws IllegalAccessException, InvocationTargetException
    {
        return method.invoke(obj, args);
    }

    public String toString() {
        return method.toString();
    }
}
