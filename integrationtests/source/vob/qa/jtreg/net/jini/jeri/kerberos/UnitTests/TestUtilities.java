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

import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.InboundRequestHandle;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.jeri.connection.ServerConnection;
import net.jini.jeri.kerberos.KerberosEndpoint;
import net.jini.jeri.kerberos.KerberosServerEndpoint;
import java.io.File;
import java.lang.reflect.Field;
import java.security.AccessControlContext;
import java.security.Permission;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.Properties;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.Subject;
import org.ietf.jgss.GSSContext;

/** Provides common utilities for tests. */
class TestUtilities extends UnitTestUtilities {

    static {
	/* -- Make sure system properties and security manager are set -- */
	Properties props = System.getProperties();
	String src = props.getProperty("test.src", ".") + File.separator;
	if (props.getProperty("java.security.policy") == null) {
	    props.setProperty("java.security.policy", src + "policy");
	}
	if (props.getProperty("net.jini.jeri.server.hostname")
	    == null)
	{
	    props.setProperty("net.jini.jeri.server.hostname",
			      "localhost");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    /* Reflection Fields */

    /** The name of the package containing the classes */
    static String PACKAGE = "net.jini.jeri.kerberos";

    private static final LazyField outboundRequestConnection =
	new LazyField("net.jini.jeri.connection",
		      "ConnectionManager$Outbound", "c");
    private static final LazyField outboundRequestHandle =
	new LazyField("net.jini.jeri.connection",
		      "ConnectionManager$Outbound", "handle");
    private static final LazyField endpointServerPrincipal =
	new LazyField("KerberosEndpoint", "serverPrincipal");

    private static final LazyField inboundRequestConnection =
	new LazyField("net.jini.jeri.connection",
		      "ServerConnectionManager$Inbound", "c");
    private static final LazyField inboundRequestHandle =
	new LazyField("net.jini.jeri.connection",
		      "ServerConnectionManager$Inbound", "handle");
    private static final LazyField inboundRequestHandleConfig = new LazyField(
	"net.jini.jeri.kerberos", "KerberosServerEndpoint" +
	"$ServerConnectionImpl$InboundRequestHandleImpl", "config");
    private static final LazyField KerberosUtilConfigEncry = new LazyField(
	"net.jini.jeri.kerberos", "KerberosUtil$Config", "encry");
    private static final LazyField serverEndpointServerPrincipal =
	new LazyField("KerberosServerEndpoint", "serverPrincipal");

    private static final LazyField connectionClientPrincipal =
	new LazyField("KerberosUtil$Connection", "clientPrincipal");
    private static final LazyField connectionDoEncryption =
	new LazyField("KerberosUtil$Connection", "doEncryption");
    private static final LazyField connectionDoDelegation =
	new LazyField("KerberosUtil$Connection", "doDelegation");
    private static final LazyField connectionGSSContext =
	new LazyField("KerberosUtil$Connection", "gssContext");

    //-----------------------------------
    //          public methods
    //-----------------------------------

    /**
     * Prints the specified debugging message if the test level is at least
     * the value specified.
     */
    public static void debugPrint(int forLevel, String message) {
	if (testLevel >= forLevel) {
	    System.out.println(message);
	}
    }

    public static Subject getLoginSubject(String loginName)
	throws LoginException
    {
	LoginContext ctx = new LoginContext(loginName);
	ctx.login();
	return ctx.getSubject();
    }

    public static AccessControlContext getContext(Permission[] perms) {
	Permissions permissions = new Permissions();
	if (perms != null) {
	    for (int i = 0; i < perms.length; i++) {
		if (perms[i] != null)
		    permissions.add(perms[i]);
	    }
	}
	return new AccessControlContext(
	    new ProtectionDomain[] {
		new ProtectionDomain(null, permissions)
	    });
    }

    /* -- Methods for accessing connections -- */

    public static ClientRequestInfo getClientRequestInfo(
	KerberosEndpoint ep, OutboundRequest r)
    {
	ClientRequestInfo info = new ClientRequestInfo();
	Connection c = (Connection) outboundRequestConnection.get(r);
	info.clientConnection = c;
	info.outboundRequestHandle =
	    (OutboundRequestHandle) outboundRequestHandle.get(r);
	info.serverPrincipal =
	    (KerberosPrincipal) endpointServerPrincipal.get(ep);
	info.clientPrincipal = 
	    (KerberosPrincipal) connectionClientPrincipal.get(c);
	info.doEncryption = // conn.encry == request.encry before req is closed
	    ((Boolean) connectionDoEncryption.get(c)).booleanValue();
	info.doDelegation =
	    ((Boolean) connectionDoDelegation.get(c)).booleanValue();
	info.gssContext = (GSSContext) connectionGSSContext.get(c);
	return info;
    }

    public static ServerRequestInfo getServerRequestInfo(
	KerberosServerEndpoint ep, InboundRequest r)
    {
	ServerRequestInfo info = new ServerRequestInfo();
	ServerConnection c = 
	    (ServerConnection) inboundRequestConnection.get(r);
	info.serverConnection = c;
	info.inboundRequestHandle =
	    (InboundRequestHandle) inboundRequestHandle.get(r);
	info.serverPrincipal = 
	    (KerberosPrincipal) serverEndpointServerPrincipal.get(ep);
	info.clientPrincipal =
	    (KerberosPrincipal) connectionClientPrincipal.get(c);
	info.doEncryption = ((Boolean) KerberosUtilConfigEncry.get(
	    inboundRequestHandleConfig.get(
		info.inboundRequestHandle))).booleanValue();
	info.doDelegation =
	    ((Boolean) connectionDoDelegation.get(c)).booleanValue();
	info.gssContext = (GSSContext) connectionGSSContext.get(c);
	return info;
    }

    //-----------------------------------
    //      package-private classes
    //-----------------------------------

    static class ConnectionInfo {
	KerberosPrincipal clientPrincipal;
	KerberosPrincipal serverPrincipal;
	boolean doDelegation;
	GSSContext gssContext;
    }

    static class ClientRequestInfo extends ConnectionInfo {
	boolean doEncryption; // encry can change on a per-request basis
	Connection clientConnection;
	OutboundRequestHandle outboundRequestHandle;

	public String toString() {
	    return "\n\tClientRequestInfo[" +
		"\n\t\tclientPrincipal = " + clientPrincipal +
		"\n\t\tserverPrincipal = " + serverPrincipal +
		"\n\t\tdoEncryption = " + doEncryption +
		"\n\t\tdoDelegation = " + doDelegation + 
		"\n\t\tgssCtxDelegState = " + gssContext.getCredDelegState() +
		"]\n\n";
	}
    }

    static class ServerRequestInfo extends ConnectionInfo {
	boolean doEncryption; // encry can change on a per-request basis
	ServerConnection serverConnection;
	InboundRequestHandle inboundRequestHandle;

	public String toString() {
	    return "\n\tServerRequestInfo[" +
		"\n\t\tclientPrincipal = " + clientPrincipal +
		"\n\t\tserverPrincipal = " + serverPrincipal +
		"\n\t\tdoEncryption = " + doEncryption +
		"\n\t\tdoDelegation = " + doDelegation + 
		"\n\t\tgssCtxDelegState = " + gssContext.getCredDelegState() +
		"]\n\n";
	}
    }
    
    //-----------------------------------
    //       private inner classes
    //-----------------------------------

    /** Like Field, but resolves field when first used */
    private static class LazyField {
	private String packageName;
	private String className;
	private String fieldName;
	private Field field;

	LazyField(String className, String fieldName) {
	    this(PACKAGE, className, fieldName);
	}

	LazyField(String packageName, String className, String fieldName) {
	    this.packageName = packageName;
	    this.className = className;
	    this.fieldName = fieldName;
	}

	/** Gets a static field */
	Object getStatic() {
	    return get(null);
	}

	/** Gets a field */
	Object get(Object object) {
	    try {
		return getField().get(object);
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}

	/** Returns the requested provider field */
	private Field getField() {
	    if (field == null) {
		try {
		    Class type = Class.forName(packageName + "." + className);
		    field = type.getDeclaredField(fieldName);
		    field.setAccessible(true);
		} catch (ClassNotFoundException e) {
		    throw unexpectedException(e);
		} catch (NoSuchFieldException e) {
		    throw unexpectedException(e);
		}
	    }
	    return field;
	}

	/** Sets a static field */
	void setStatic(Object value) {
	    set(null, value);
	}

	/** Sets a field */
	void set(Object object, Object value) {
	    try {
		getField().set(object, value);
	    } catch (Exception e) {
		throw unexpectedException(e);
	    }
	}
    }
}
