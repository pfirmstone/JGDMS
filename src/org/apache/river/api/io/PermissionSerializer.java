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

package org.apache.river.api.io;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Permission;
import java.security.UnresolvedPermission;
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.impl.Messages;

/**
 * An example of pedantic checking of invariants during atomic de-serialization.
 * 
 * We will need to do this for all java platform objects that have invariants
 * and serial form that can be passed to a constructor, so we can include it in
 * the @AtomicSerial spec.
 * 
 * Note how the transient field is populated by the @AtomicSerial constructor
 * because it calls the standard constructor after checking invariants and
 * creating the permission instance.
 * 
 * Presently this class doesn't presently work with standard java serialization, 
 * it would if we implemented readObject(), but then it's not a publicly 
 * published class anyway.
 * 
 * @author peter
 */
@Serializer(replaceObType = Permission.class)
@AtomicSerial
final class PermissionSerializer implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("targetType", Class.class),
	    new ObjectStreamField("unresolvedType", String.class),
	    new ObjectStreamField("targetName", String.class),
	    new ObjectStreamField("targetActions", String.class)
	};
    
    // Empty set of arguments to default constructor of a Permission.
    private static final Class[] NO_ARGS = {};

    // One-arg set of arguments to default constructor of a Permission.
    private static final Class[] ONE_ARGS = { String.class };

    // Two-args set of arguments to default constructor of a Permission.
    private static final Class[] TWO_ARGS = { String.class, String.class };
    
    /**
     * Tries to find a suitable constructor and instantiate a new Permission
     * with specified parameters. 
     * 
     * Duplicated from PolicyUtils. - Consolidate when Java 9 is default.
     *
     * @param targetType class of expected Permission instance
     * @param targetName name of expected Permission instance
     * @param targetActions actions of expected Permission instance
     * @return a new Permission instance
     * @throws IllegalArgumentException if no suitable constructor found
     * @throws InstantiationException any exception thrown by Constructor.newInstance()
     */
    static Permission instantiatePermission(Class<?> targetType,
            String targetName, String targetActions)
            throws InstantiationException, IllegalAccessException, 
            IllegalArgumentException, InvocationTargetException 
    {

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
        throw new IllegalArgumentException(
                Messages.getString("security.150", targetType));//$NON-NLS-1$
    }
    
    private static Permission create(
	    Class<? extends Permission> targetType,
	    String unresolvedType,
	    String targetName,
	    String targetActions
	    ) throws ObjectStreamException
    {
	check(targetType, unresolvedType);
	if (unresolvedType != null) return 
		new UnresolvedPermission(unresolvedType, targetName, targetActions, null);
	try {
	    Permission result = instantiatePermission(targetType, targetName, targetActions);
	    result.getActions(); //Defeat lazy initialization, for safe publication.
	    return result;
	} catch (InstantiationException ex) {
	    throw objectStreamException(ex, targetType);
	} catch (IllegalAccessException ex) {
	    throw objectStreamException(ex, targetType);
	} catch (IllegalArgumentException ex) {
	    throw objectStreamException(ex, targetType);
	} catch (InvocationTargetException ex) {
	    throw objectStreamException(ex, targetType);
	}
    }
    
    private static ObjectStreamException objectStreamException(Exception ex, Class targetType) {
	ObjectStreamException e = new InvalidClassException(targetType.toString(),
		"Unable to create an instance");
	e.initCause(ex);
	return e;
    }
    
    /* Input validation */
    private static void check(Class<?> targetType, String unresolvedType) 
	    throws ObjectStreamException 
    {
	if (unresolvedType == null && UnresolvedPermission.class.equals(targetType)) 
	    throw new InvalidObjectException(
		    "UnresolvedPermission cannot have a null unresolved type");
	try {
	    targetType.asSubclass(Permission.class);
	} catch (NullPointerException e){
	    ObjectStreamException ex = 
		    new InvalidObjectException("targetType cannot be null");
	    ex.initCause(e);
	    throw ex;
	} catch (ClassCastException e){
	    ObjectStreamException ex = new InvalidObjectException(
		    "targetType must be a sublcass of Permission");
	    ex.initCause(e);
	    throw ex;
	}
    }
    
    private final Class<? extends Permission> targetType;
    private final String unresolvedType;
    private final String targetName;
    private final String targetActions;
    private final /*transient*/ Permission permission;
    
    /**
     * We can only validate that targetClass is non null and an instance of 
     * Permission.
     * 
     * @param arg
     * @throws IOException 
     */
    PermissionSerializer (GetArg arg) throws IOException {
	this( 
	    create(
		arg.get("targetType", null, Class.class),
		arg.get("type", null, String.class),
		arg.get("targetName", null, String.class),
		arg.get("targetActions", null, String.class) 
	    )
	);
    }
    
    private PermissionSerializer (  Class<? extends Permission> targetType,
				String type,
				String targetName, 
				String targetActions, 
				Permission permission)
    {
	this.targetType = targetType;
	this.unresolvedType = type;
	this.targetName = targetName;
	this.targetActions = targetActions;
	this.permission = permission;
    }
    
    /**
     * 
     * @param perm 
     * @throws NullPointerException
     */
    PermissionSerializer(Permission perm){
	this(
	    perm.getClass(),
	    perm instanceof UnresolvedPermission ? 
		    ((UnresolvedPermission) perm).getUnresolvedType(): null,
	    perm.getName(),
	    perm.getActions(),
	    perm
	);
    }
    
    /*
     * Permission is not included in hashCode or equals calculation as doing so
     * can cause network calls.
     */

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 67 * hash + Objects.hashCode(this.targetType);
	hash = 67 * hash + Objects.hashCode(this.unresolvedType);
	hash = 67 * hash + Objects.hashCode(this.targetName);
	hash = 67 * hash + Objects.hashCode(this.targetActions);
	return hash;
    }
    
    @Override
    public boolean equals(Object obj){
	if (this == obj) return true;
	if(!(obj instanceof PermissionSerializer)) return false;
	PermissionSerializer that = (PermissionSerializer) obj;
	if (! Objects.equals(targetType, that.targetType)) return false;
	if (! Objects.equals(unresolvedType, that.unresolvedType )) return false;
	if (! Objects.equals(targetName, that.targetName)) return false;
	return Objects.equals(targetActions, that.targetActions);
    }
	
    Object readResolve() throws ObjectStreamException {
	return permission;
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	PutField pf = out.putFields();
	pf.put("targetType", targetType);
	pf.put("unresolvedtype", unresolvedType);
	pf.put("targetName", targetName);
	pf.put("targetActions", targetActions);
	out.writeFields();
    }
}
