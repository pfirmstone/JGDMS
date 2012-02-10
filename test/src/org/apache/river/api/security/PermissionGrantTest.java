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

package org.apache.river.api.security;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import org.junit.Test;
import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.*;

import java.net.URL;
import java.security.cert.Certificate;
import java.security.CodeSource;
import java.security.Principal;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;


/**
 * Null CodeSource of PermissionGrant implies any CodeSource; non-null
 * CodeSource should delegate to its own imply() functionality
 * @throws java.lang.Exception 
 */
public class PermissionGrantTest {
    PermissionGrantBuilder pgb;
    CodeSource cs0, cs10, cs11, cs12, cs13, cs20, cs21, cs22, cs23, cs30, cs31,
            cs32, cs33;
    PermissionGrant pe0, pe10, pe11, pe12, pe13, pe20, pe21, pe22, pe23, pe30,
            pe31, pe32, pe33;
    CertificateFactory cf;
    Certificate[] certs1, certs2;
    
    public PermissionGrantTest(){
	
    }
    
    @Before
    public void setUp() throws MalformedURLException, URISyntaxException {
	try {
	    cf = CertificateFactory.getInstance("X.509");
	} catch ( CertificateException e) {
	    cf = null;
	}
        pgb = PermissionGrantBuilder.newBuilder();
        cs0  = new CodeSource(null, (Certificate[]) null);
        cs10 = new CodeSource(new URL("file:"), (Certificate[]) null);
        cs11 = new CodeSource(new URL("file:/"), (Certificate[]) null);
        cs12 = new CodeSource(new URL("file://"), (Certificate[]) null);
        cs13 = new CodeSource(new URL("file:///"), (Certificate[]) null);

        cs20 = new CodeSource(new URL("file:*"), (Certificate[]) null);
        cs21 = new CodeSource(new URL("file:/*"), (Certificate[]) null);
        cs22 = new CodeSource(new URL("file://*"), (Certificate[]) null);
        cs23 = new CodeSource(new URL("file:///*"), (Certificate[]) null);

        cs30 = new CodeSource(new URL("file:-"), (Certificate[]) null);
        cs31 = new CodeSource(new URL("file:/-"), (Certificate[]) null);
        cs32 = new CodeSource(new URL("file://-"), (Certificate[]) null);
        cs33 = new CodeSource(new URL("file:///-"), (Certificate[]) null);

        pe0 = pgb.codeSource(null)
                .principals(null)
                .permissions(null)
                .context(PermissionGrantBuilder.CODESOURCE)
                .build();
        
        pe10 = pgb.codeSource(cs10).build();
        pe11 = pgb.codeSource(cs11).build();
        pe12 = pgb.codeSource(cs12).build();
        pe13 = pgb.codeSource(cs13).build();
         
        pe20 = pgb.codeSource(cs20).build();
        pe21 = pgb.codeSource(cs21).build();
        pe22 = pgb.codeSource(cs22).build();
        pe23 = pgb.codeSource(cs23).build();

        pe30 = pgb.codeSource(cs30).build();
        pe31 = pgb.codeSource(cs31).build();
        pe32 = pgb.codeSource(cs32).build();
        pe33 = pgb.codeSource(cs33).build();
    }

    @After
    public void tearDown() {
    }
    
    @Test
    public void test1() {
        assertFalse (pe0.implies( (CodeSource) null, (Principal[])null )); // Originally was assert true.
    }

    @Test
    public void test2() {        
        assertTrue (pe0.implies(cs10, (Principal[])null ));
    }
    
    @Test
    public void test3() {
        assertTrue (pe0.implies(cs11, (Principal[])null ));
    }
    
    @Test
    public void test4() {
        assertTrue (pe0.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test5() {
        assertTrue (pe0.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test6() {
        assertTrue (pe0.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test7() {
        assertTrue (pe0.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test8() {
        assertTrue (pe0.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test9() {
        assertTrue (pe0.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test10() {
        assertTrue (pe0.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test11() {
        assertTrue (pe0.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test12() {
        assertTrue (pe0.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test13() {
        assertTrue (pe0.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test14() {
        assertFalse(pe10.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test15() {
        assertTrue (pe10.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test16() {
        assertFalse(pe10.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test17() {
        assertTrue (pe10.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test18() {
        assertFalse(pe10.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test19() {
        assertTrue (pe10.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test20() {
        assertFalse(pe10.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test21() {
        assertFalse(pe10.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test22() {
        assertFalse(pe10.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test23() {
        assertTrue (pe10.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test24() {
        assertFalse(pe10.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test25() {
        assertFalse(pe10.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test26() {
        assertFalse(pe10.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test27() {
        assertFalse(pe11.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test28() {
        assertFalse(pe11.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test29() {
        assertTrue (pe11.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test30() {
        assertFalse(pe11.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test31() {
        assertTrue (pe11.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test32() {
        assertFalse(pe11.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test33() {
        assertFalse(pe11.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test34() {
        assertFalse(pe11.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test35() {
        assertFalse(pe11.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test36() {
        assertFalse(pe11.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test37() {
        assertFalse(pe11.implies(cs31, (Principal[])null ));
    }
   @Test
    public void test38() { 
        assertFalse(pe11.implies(cs32, (Principal[])null ));
   }
    @Test
    public void test39() {
        assertFalse(pe11.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test40() {
        assertFalse(pe12.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test41() {
        assertTrue (pe12.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test42() {
        assertFalse(pe12.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test43() {
        assertTrue (pe12.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test44() {
        assertFalse(pe12.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test45() {
        assertTrue (pe12.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test45a() {
        assertFalse(pe12.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test46() {   
        assertFalse(pe12.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test47() {    
        assertFalse(pe12.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test48() {    
        assertTrue (pe12.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test49() {   
        assertFalse(pe12.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test50() {    
        assertFalse(pe12.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test51() {  
        assertFalse(pe12.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test52() {  
        assertFalse(pe13.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test53() {    
        assertFalse(pe13.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test54() {    
        assertTrue (pe13.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test55() {    
        assertFalse(pe13.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test56() {    
        assertTrue (pe13.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test57() {    
        assertFalse(pe13.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test58() {    
        assertFalse(pe13.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test59() {    
        assertFalse(pe13.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test60() {    
        assertFalse(pe13.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test61() {    
        assertFalse(pe13.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test62() {   
        assertFalse(pe13.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test63() {    
        assertFalse(pe13.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test64() {    
        assertFalse(pe13.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test65() {
        assertFalse(pe20.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test66() {     
        assertTrue (pe20.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test67() {    
        assertFalse(pe20.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test68() {    
        assertTrue (pe20.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test69() {     
        assertFalse(pe20.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test70() {    
        assertTrue (pe20.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test71() {    
        assertFalse(pe20.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test72() {    
        assertFalse(pe20.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test73() {       
        assertFalse(pe20.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test74() {     
        assertTrue (pe20.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test75() {     
        assertFalse(pe20.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test76() {     
        assertFalse(pe20.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test77() {     
        assertFalse(pe20.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test78() { 
        assertFalse(pe21.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test79() {     
        assertFalse(pe21.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test80() {     
        assertTrue (pe21.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test81() {     
        assertFalse(pe21.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test82() {     
        assertTrue (pe21.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test83() {     
        assertFalse(pe21.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test84() {     
        assertTrue (pe21.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test85() {     
        assertFalse(pe21.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test86() {     
        assertTrue (pe21.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test87() {     
        assertFalse(pe21.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test88() {     
        assertTrue (pe21.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test89() {    
        assertFalse(pe21.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test90() {     
        assertTrue (pe21.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test91() {
        assertFalse(pe22.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test92() {     
        assertFalse(pe22.implies(cs10, (Principal[])null ));
    }
//    @Test
//    public void test93() {     
//         assertFalse(pe22.implies(cs11, (Principal[]) null));
//    }
    @Test
    public void test94() {     
        assertFalse(pe22.implies(cs12, (Principal[])null ));
    }
//    @Test
//    public void test95() {    
//         assertFalse(pe22.implies(cs13,(Principal[])null ));
//    }
    @Test
    public void test96() { 
        assertFalse(pe22.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test97() {     
        assertFalse(pe22.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test98() {     
        assertTrue (pe22.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test99() {     
        assertFalse(pe22.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test100() {     
        assertFalse(pe22.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test101() {     
        assertFalse(pe22.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test102() {     
        assertTrue (pe22.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test103() {     
        assertFalse(pe22.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test104() { 
        assertFalse(pe23.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test105() {     
        assertFalse(pe23.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test106() {     
        assertTrue (pe23.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test107() {     
        assertFalse(pe23.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test108() {     
        assertTrue (pe23.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test109() {     
        assertFalse(pe23.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test110() {     
        assertTrue (pe23.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test111() {     
        assertFalse(pe23.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test112() {     
        assertTrue (pe23.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test113() {    
        assertFalse(pe23.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test114() {     
        assertTrue (pe23.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test115() {     
        assertFalse(pe23.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test116() {     
        assertTrue (pe23.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test117() { 
        assertFalse(pe30.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test118() {    
        assertTrue (pe30.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test119() {     
        assertFalse(pe30.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test120() {     
        assertTrue (pe30.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test121() {     
        assertFalse(pe30.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test122() {     
        assertTrue (pe30.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test123() {     
        assertFalse(pe30.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test124() {     
        assertFalse(pe30.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test125() {     
        assertFalse(pe30.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test126() {     
        assertTrue (pe30.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test127() {     
        assertFalse(pe30.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test128() {     
        assertFalse(pe30.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test129() { 
        assertFalse(pe30.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test130() { 
        assertFalse(pe31.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test131() { 
        assertTrue (pe31.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test132() {     
        assertTrue (pe31.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test133() {     
        assertTrue (pe31.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test134() {     
        assertTrue (pe31.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test135() {    
        assertTrue (pe31.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test136() {        
        assertTrue (pe31.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test137() {       
        assertFalse(pe31.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test138() {        
        assertTrue (pe31.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test139() {        
        assertTrue (pe31.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test140() {        
        assertTrue (pe31.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test141() {        
        assertFalse(pe31.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test142() {        
        assertTrue (pe31.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test143() {    
        assertFalse(pe32.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test144() {       
        assertFalse(pe32.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test145() {        
        assertFalse(pe32.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test146() {        
        assertFalse(pe32.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test147() {        
        assertFalse(pe32.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test148() {        
        assertFalse(pe32.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test149() {        
        assertFalse(pe32.implies(cs21, (Principal[])null ));
    }
//    @Test
//    public void test150() {        
//        assertFalse(pe32.implies(cs22, (Principal[])null ));
//    }
    @Test
    public void test151() {        
        assertFalse(pe32.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test152() {        
        assertFalse(pe32.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test153() {        
        assertFalse(pe32.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test154() {        
        assertTrue (pe32.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test155() {        
        assertFalse(pe32.implies(cs33, (Principal[])null ));
    }
    @Test
    public void test156() {    
        assertFalse(pe33.implies((CodeSource)null, (Principal[])null ));
    }
    @Test
    public void test157() {       
        assertTrue (pe33.implies(cs10, (Principal[])null ));
    }
    @Test
    public void test158() {        
        assertTrue (pe33.implies(cs11, (Principal[])null ));
    }
    @Test
    public void test159() {        
        assertTrue (pe33.implies(cs12, (Principal[])null ));
    }
    @Test
    public void test160() {        
        assertTrue (pe33.implies(cs13, (Principal[])null ));
    }
    @Test
    public void test161() {        
        assertTrue (pe33.implies(cs20, (Principal[])null ));
    }
    @Test
    public void test162() {        
        assertTrue (pe33.implies(cs21, (Principal[])null ));
    }
    @Test
    public void test163() {        
        assertFalse(pe33.implies(cs22, (Principal[])null ));
    }
    @Test
    public void test165() {        
        assertTrue (pe33.implies(cs23, (Principal[])null ));
    }
    @Test
    public void test166() {        
        assertTrue (pe33.implies(cs30, (Principal[])null ));
    }
    @Test
    public void test167() {        
        assertTrue (pe33.implies(cs31, (Principal[])null ));
    }
    @Test
    public void test168() {        
        assertFalse(pe33.implies(cs32, (Principal[])null ));
    }
    @Test
    public void test169() {        
        assertTrue (pe33.implies(cs33, (Principal[])null ));
    }
}
