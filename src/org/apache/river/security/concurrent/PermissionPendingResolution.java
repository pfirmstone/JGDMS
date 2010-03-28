/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.security.concurrent;

import java.lang.reflect.Constructor;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.UnresolvedPermission;
import java.security.cert.Certificate;

/**
 *
 * @author Peter Firmstone
 */
class PermissionPendingResolution extends Permission {
        private static final long serialVersionUID = 1L;
        private transient String type; //Class name of underlying permission
        private transient String name; //Target name of underlying permission
        private transient String actions;
        /* We have our own array copy of certs, prevents unnecessary 
         * array creation every time .getUnresolvedCerts() is called.
         */ 
        private transient Certificate [] targetCerts;
        private UnresolvedPermission unresolvedPermission;
    
    PermissionPendingResolution(UnresolvedPermission up){
        super(up.getUnresolvedType());
        type = up.getUnresolvedType();
        name = up.getUnresolvedName();
        actions = up.getUnresolvedActions();
        // don't need to defensive copy, UnresolvedPermission already does it.
        targetCerts = up.getUnresolvedCerts();
        unresolvedPermission = up;
    }
    
    Permission resolve(Class targetType) {
        // check signers at first
        if (matchSubset( targetCerts, targetType.getSigners())) {
            try {
                 return instantiatePermission(targetType, name, actions);
            } catch (Exception ignore) {
                //TODO log warning?
            }
        }
        return null;
    }
    
    Permission resolve(ClassLoader cl){
        Class<?> targetType = null;
        try {
           targetType =  cl.loadClass(type);
        } catch (ClassNotFoundException e){
            //TODO log warning?
            System.err.println(type +" " + name + " " + actions +
                    ": Cannot be resolved due to ClassNotFoundException");
            e.printStackTrace();
        } catch (NullPointerException e){
            //TODO log warning, this should never happen but if it does
            //the class will not be resolved.
            System.err.println(type +" " + name + " " + actions +
                    ": Cannot be resolved due to ClassLoader null instance");
            e.printStackTrace();
        }
        if ( targetType == null ) {return null;}
        return resolve(targetType);
    }
    

    /**
     * Code Copied, Courtesey Apache Harmony
     * 
     * Checks whether the objects from <code>what</code> array are all
     * presented in <code>where</code> array.
     * 
     * @param what first array, may be <code>null</code> 
     * @param where  second array, may be <code>null</code>
     * @return <code>true</code> if the first array is <code>null</code>
     * or if each and every object (ignoring null values) 
     * from the first array has a twin in the second array; <code>false</code> otherwise
     */
     boolean matchSubset(Object[] what, Object[] where) {
        if (what == null) {
            return true;
        }

        for (int i = 0; i < what.length; i++) {
            if (what[i] != null) {
                if (where == null) {
                    return false;
                }
                boolean found = false;
                for (int j = 0; j < where.length; j++) {
                    if (what[i].equals(where[j])) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }
    
    // Empty set of arguments to default constructor of a Permission.
    private static final Class[] NO_ARGS = {};

    // One-arg set of arguments to default constructor of a Permission.
    private static final Class[] ONE_ARGS = { String.class };

    // Two-args set of arguments to default constructor of a Permission.
    private static final Class[] TWO_ARGS = { String.class, String.class };
       
    /**
     * Code copied, courtsey of Apache Harmony
     * 
     * Tries to find a suitable constructor and instantiate a new Permission
     * with specified parameters.  
     *
     * @param targetType class of expected Permission instance
     * @param targetName name of expected Permission instance
     * @param targetActions actions of expected Permission instance
     * @return a new Permission instance
     * @throws IllegalArgumentException if no suitable constructor found
     * @throws Exception any exception thrown by Constructor.newInstance()
     */
    Permission instantiatePermission(Class<?> targetType,
            String targetName, String targetActions) throws Exception {

        // let's guess the best order for trying constructors
        Class[][] argTypes = null;
        Object[][] args = null;
        if (targetActions != null) {
            argTypes = new Class[][] { TWO_ARGS, ONE_ARGS, NO_ARGS };
            args = new Object[][] { { targetName, targetActions },
                    { targetName }, {} };
        } else if (targetName != null) {
            argTypes = new Class[][] { ONE_ARGS, TWO_ARGS, NO_ARGS };
            args = new Object[][] { { targetName },
                    { targetName, targetActions }, {} };
        } else {
            argTypes = new Class[][] { NO_ARGS, ONE_ARGS, TWO_ARGS };
            args = new Object[][] { {}, { targetName },
                    { targetName, targetActions } };
        }

        // finally try to instantiate actual permission
        for (int i = 0; i < argTypes.length; i++) {
            try {
                Constructor<?> ctor = targetType.getConstructor(argTypes[i]);
                return (Permission)ctor.newInstance(args[i]);
            }
            catch (NoSuchMethodException ignore) {}
        }
        throw new IllegalArgumentException(type + name + actions);//$NON-NLS-1$
    }

    @Override
    public boolean implies(Permission permission) {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if ( obj == this ) {return true;}
        if ( !(obj instanceof PermissionPendingResolution)) {return false;}
        PermissionPendingResolution ob = (PermissionPendingResolution) obj;
        if (this.unresolvedPermission.equals(ob.unresolvedPermission)) {return true;}
        return false;
    }

    @Override
    public int hashCode() {
        return unresolvedPermission.hashCode();
    }

    @Override
    public String getActions() {
        return "";
    }
    
    @Override
    public PermissionCollection newPermissionCollection(){
        return new PermissionPendingResolutionCollection();
    }
    
    public UnresolvedPermission asUnresolvedPermission(){
        return unresolvedPermission;
    }
}
