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
package net.jini.io;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Contains Package information from a jar files Manifest utilising the
 * java ClassLoader.
 * 
 * It is expected that these packages be utilised to create some kind of weak
 * hashtable cache that looks up ClassLoaders from a PackageVersion for Class
 * resolution.
 * 
 * OSGi or other frameworks can utilise this information to decide package
 * imports.
 * 
 * I'm also considering a jini service which provides version compatibility
 * information, along with a codebase service.
 * 
 * @author Peter Firmstone
 * @see java.lang.Package
 * @see java.lang.ClassLoader
 */
public final class PackageVersion implements Serializable {
    private static final Map<Integer,PackageVersion> pool 
            = new WeakHashMap<Integer,PackageVersion>(60);
    private static final long serialVersionUID = 1L;
    private static final String EMPTY = new String();
    private final String packageName; // Cannot be null
    // Allowing null is a pain as it requires additional checks
    // however the tradeoff is worth it for Serialization performance
    private final String implementationVendor; // Can be null
    private final String implementationVersion; // Can be null
    private transient int hash;
    // Specification isn't important as a spec only represents the public API
    // an object is specific to the implementation.
    // Title probably doesn't matter either. Perhaps the implementation Vendor
    // doesn't either, however I've left it in place.
    private PackageVersion(String pkgName, String impVendor, String impVersion,
            Integer hashCode){
        packageName = pkgName;
        implementationVendor = impVendor;
        implementationVersion = impVersion;        
        hash = hashCode.intValue();
    }
    
    public String getPkgName(){
        return packageName;
    }
    
    public String getImplVendor(){
        if (implementationVendor == null) return EMPTY;
        return implementationVendor;
    }
    
    public String getImplVersion(){
        if (implementationVersion == null) return EMPTY;
        return implementationVersion;
    }
    
    public static PackageVersion getInstance(String pkgName,
            String impVendor, String impVersion){
        if (pkgName == null) throw new NullPointerException("Package" +
                " name cannot be null");
        Integer hashCode = hashCode( pkgName, impVendor, impVersion);
        PackageVersion pv = null;
        boolean wasNull = false;
        synchronized (pool) {
            pv = pool.get(hashCode);
            if (pv == null) {
                wasNull = true;
                pv = new PackageVersion(pkgName, impVendor, impVersion, hashCode);
                pool.put(hashCode, pv);
            }
        }
        // Just in case something has the same hashcode but not equal -unlikely
        if ( !wasNull  ) {
            if ( (pv.implementationVendor == impVendor || 
                    pv.implementationVendor.equals(impVendor)) &&
                    (pv.implementationVersion == impVersion || 
                    pv.implementationVersion.equals(impVersion)) &&
                    pv.packageName.equals(pkgName)){ 
                return pv;
            } else {
                // This is rare there must be an identical hash value.
               return new PackageVersion(pkgName, impVendor, impVersion, hashCode);
            }
        }
        // pv was null, just return the new one.
        return pv;
    }
    
    public static PackageVersion getInstance(Package pkg){
        String pkgName = pkg.getName();
        String impVendor = pkg.getImplementationVendor();
        String impVersion = pkg.getImplementationVersion();
        return getInstance(pkgName, impVendor, impVersion);
    }
    
    public static PackageVersion getInstance(Object obj){
        Class clazz = obj.getClass();
        Package pkg = getPackage(clazz);
        return getInstance(pkg);
    }
    
    
    private static Package getPackage(Class type){
                    Package pkg = type.getPackage();
                    if (pkg != null) return pkg;
                    String name = type.getName();
                    String pkgName = getPackageName(name);
                    // Arrays strings return null packages, should we return the package
                    // belonging to the class withing the array?
                    return pkg.getPackage(pkgName);
                }
                
    private static String getPackageName(String className){
        int index = className.lastIndexOf('.');
        if (index != -1) {
             return className.substring(0, index);
        }
        return "";
    }
    
    @Override
    public boolean equals(Object obj){
        if (this == obj) return true;
        if ( obj instanceof PackageVersion ) 
        {
            PackageVersion pkg = (PackageVersion) obj;
            if ( 
                (implementationVendor == pkg.implementationVendor || 
                implementationVendor.equals(pkg.implementationVendor)) &&
                (implementationVersion == pkg.implementationVersion || 
                implementationVersion.equals(pkg.implementationVersion)) &&
                packageName.equals(pkg.packageName)) 
            {
                return true;
            }
        }
        return false;      
    }
    
    @Override
    public int hashCode(){
        return hash;
    }
    
    private static Integer hashCode(String pkgName, String impVendor,
            String impVersion){
        if (impVendor == null) impVendor = EMPTY;
        if (impVersion == null) impVersion = EMPTY;
        return ( pkgName.hashCode() - impVendor.hashCode() +
                impVersion.hashCode());
    }
    
    public boolean equalsPackage(Package pkg){
        String impVendor = pkg.getImplementationVendor();
        String impVersion = pkg.getImplementationVersion();        
        if ( 
            (implementationVendor == impVendor || 
            implementationVendor.equals(impVendor)) &&
            (implementationVersion == impVersion || 
            implementationVersion.equals(impVersion)) &&
            packageName.equals(pkg.getName())) 
        {
            return true;
        }
        return false;
    }
    
    @Override
    public String toString(){
        return "package " + getPkgName() + "|" + getImplVendor() + "|" +
                getImplVersion();
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException{
        out.defaultWriteObject();
    }
    
    
    /**
     * readObject ensures we add any new instances to the pool, while
     * readResolve ensures that external usages don't refer to duplicates, so
     * any duplicates can be garbage collected.  The pool is a weak reference
     * pool, so pool instances will be garbage collected too, if no longer
     * reachable from a strong reference.
     * @param in
     * @throws java.io.IOException
     * @throws java.lang.ClassNotFoundException
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException{
        in.defaultReadObject();
        // Strings are immutable and immune to unmarshalling reference stealing
        // attacks with final references.
        // Hash is transient set it to a value consistent with the local jvm.
        if (packageName == null) throw new NullPointerException("Package" +
                " name cannot be null");
        hash = hashCode(packageName, implementationVendor, implementationVersion);
        Integer key = new Integer(hash);
        synchronized (pool){
            if ( !pool.containsKey(key)) {
                pool.put(key,this);
            }
        }
    }
    
    /**
     * This method just ensure's we use our local jvm pool objects, instead of
     * unmarshalled duplicates, so as not to cause a memory explosion, that is 
     * an instance for every class in every package.  
     * 
     * If communication only occured with one remote host, we could leave
     * the responsibility of ensuring all marshalled instances referenced the
     * same PackageVersion object in the Serialization Object Graph, however 
     * in an environment with many hosts, we couldn't rely on that mechanism.
     * 
     * The readObject method ensures that any new objects are placed into the
     * pool. A concurrently accessible pool is not deemed necessary since 
     * the network is much slower than the CPU and the synchronized block 
     * is small. 
     * 
     * Any duplicates created by the readObject method will not be strongly
     * referenced and can be collected by the garbage collector.
     * 
     * @return
     * @SerializedForm
     */
    private Object readResolve() {
        return getInstance(packageName, implementationVendor, implementationVersion);
    }
}
