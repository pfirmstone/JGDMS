/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.jini.tool.classdepend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Peter Firmstone
 */
public class ClassDependencyTreeNodes {
    /* This will get changed to a concurrentHashMap when River transitions to
     * JDK 1.5
     */
    private final Map callers = Collections.synchronizedMap( new HashMap());
    
    public ClassDependencyTreeNodes(){   
    }
    
    public void addNode (String callingClass, String dependencyClass, String method){
        synchronized (callers){
            if (callers.containsKey(callingClass)){
                ClassDependencyBranch cdpb = (ClassDependencyBranch) callers.get(method);
                cdpb.addMethod(dependencyClass, method);
            }else {
                ClassDependencyBranch cdpb = new ClassDependencyBranch(callingClass);
                cdpb.addMethod(dependencyClass, method);
                callers.put(callingClass, cdpb);
            }
        }
    }
    
    public List getDependencies ( String callingClass){
        List deps;
        synchronized (callers) {
            deps = ( (ClassDependencyBranch) callers.get(callingClass)).getClassesCalled();
        }
        return deps;
    }
    
    public List getAllDependencies (){
        List deps = new ArrayList();
        synchronized (callers) {
            Iterator itr = callers.values().iterator();
            while (itr.hasNext()){
                deps.addAll( ( (ClassDependencyBranch) itr.next()).getClassesCalled());
            }
        }
        return deps;
    }
    
    /*
    public List getDependencyDetails( String callingClass){
        
    }
     */
    
    static class ClassDependencyBranch {
        /* The keys are the fully qualified names of methods called, the values, 
        * the String values of the class's fully qualified name containing the
        * method called.  The calling class is stored as a string.
        */ 
        private final Map methodsCalled = Collections.synchronizedMap(new HashMap()); 
        private final String callingClass;

        public ClassDependencyBranch (String callingClass){
            this.callingClass = callingClass;
        }
    
        public void addMethod(String classCalled, String method){
            synchronized (methodsCalled) {
                if (methodsCalled.containsKey(classCalled)) {
                    Set methods = (Set) methodsCalled.get(classCalled);
                    methods.add(method);
                }else {
                    /* this set is only ever accessed through the enclosing 
                    * synchronized HashMap so it will never be accessed by
                    * concurrent threads, unless its reference escapes of course.
                    */   
                    Set methods = new HashSet();
                    methods.add(method); //Only if not already present.
                    methodsCalled.put(classCalled, methods);
                }
            }
        
        }
    
        public String getCallingClass() {
            return callingClass;
        }
    
        public List calls() {
            List calls = new ArrayList();
            synchronized (methodsCalled) {
                Iterator itr = methodsCalled.keySet().iterator();
                while (itr.hasNext()) {
                    String key = (String) itr.next();
                    Iterator itrMethods = ( (Set) methodsCalled.get(key)).iterator();
                    while (itrMethods.hasNext()){
                        String method = (String) itrMethods.next();
                        String call = callingClass + " called class: " + key + " via method: " + method;
                        calls.add(call);
                    }
                }
            }
            return calls;
        }
    
        public List calls(String classCalled){
            List calls = new ArrayList();
            synchronized (methodsCalled) {
                Iterator itr = ( (Set) methodsCalled.get(classCalled)).iterator();
                while (itr.hasNext()){
                    calls.add(itr.next());
                }
            }
            return calls;
        }
    
        public List getClassesCalled () {
            List calls = new ArrayList();
            synchronized (methodsCalled) {
                Iterator itr = methodsCalled.keySet().iterator();
                while (itr.hasNext()){
                    calls.add(itr.next());
                }
            }
            return calls;
        }
    }
    
    
}
