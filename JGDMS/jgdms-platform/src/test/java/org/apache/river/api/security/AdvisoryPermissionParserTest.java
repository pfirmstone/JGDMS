

package org.apache.river.api.security;

import java.security.Permission;
import java.util.PropertyPermission;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author peter
 */
public class AdvisoryPermissionParserTest {
    
    public AdvisoryPermissionParserTest() {
    }

    /**
     * Test of parse method, of class AdvisoryPermissionParser.
     */
    @Test
    public void testParse() {
	System.out.println("parse");
	String encodedPermission ="(java.util.PropertyPermission \"org.apache.river.outrigger.maxServerQueryTimeout\" \"read\")";
	ClassLoader loader = null;
	Permission expResult = new PropertyPermission("org.apache.river.outrigger.maxServerQueryTimeout", "read");
	Permission result = AdvisoryPermissionParser.parse(encodedPermission, loader);
	assertEquals(expResult, result);
	// TODO review the generated test code and remove the default call to fail.
//	fail("The test case is a prototype.");
    }

    /**
     * Test of getEncoded method, of class AdvisoryPermissionParser.
     */
    @Test
    public void testGetEncoded_Permission() {
	System.out.println("getEncoded");
	Permission perm = new PropertyPermission("org.apache.river.outrigger.maxServerQueryTimeout", "read");
	String expResult = "(java.util.PropertyPermission \"org.apache.river.outrigger.maxServerQueryTimeout\" \"read\")";
	String result = AdvisoryPermissionParser.getEncoded(perm);
	assertEquals(expResult, result);
	// TODO review the generated test code and remove the default call to fail.
//	fail("The test case is a prototype.");
    }

    
}
