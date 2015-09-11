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

package org.apache.river.proxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.server.RMIClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.loader.ClassLoading;

/**
 *
 * @author peter
 */
public final class CodebaseAnnotation {
    private CodebaseAnnotation(){
	throw new AssertionError();
    }
    
    public static String getClassAnnotation(Class clas){
	try {
	    Method m = ClassLoading.class.getMethod("getClassAnnotation", Class.class);
	    return (String) m.invoke(null, clas);
	} catch (NoSuchMethodException e){
	    // Ignore, earlier version of River.
	} catch (IllegalAccessException ex) {
	    Logger.getLogger(CodebaseAnnotation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (IllegalArgumentException ex) {
	    Logger.getLogger(CodebaseAnnotation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (InvocationTargetException ex) {
	    Logger.getLogger(CodebaseAnnotation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (SecurityException ex){
	    Logger.getLogger(CodebaseAnnotation.class.getName()).log(Level.CONFIG, null, ex);
	}
	return RMIClassLoader.getClassAnnotation(clas);
    }
}
