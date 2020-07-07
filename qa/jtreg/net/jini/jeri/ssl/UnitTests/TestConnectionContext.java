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
/* @test 
 * @summary Tests the ConnectionContext class
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test TestUtilities
 * @run main/othervm/policy=policy TestConnectionContext
 */

import java.security.Principal;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.*;
import net.jini.core.constraint.*;
import net.jini.jeri.ssl.ConfidentialityStrength;
import java.util.Arrays;

/** Test ConnectionContext */
public class TestConnectionContext extends TestUtilities implements Test {
    static class Enum {
	private final String name;
	Enum(String name) { this.name = name; }
    }

    private static final LazyMethod getInstance =
	new LazyMethod(
	    "ConnectionContext", "getInstance",
	    new Class[] { String.class /* cipherSuite */,
			  Principal.class /* client */,
			  Principal.class /* server */,
			  boolean.class /* integrity */,
			  boolean.class /* clientSide */,
			  InvocationConstraints.class /* constraints */ });
    private static final LazyMethod getConnectionTime =
	new LazyMethod("ConnectionContext", "getConnectionTime", new Class[0]);
    private static final LazyMethod getPreferences =
	new LazyMethod("ConnectionContext", "getPreferences", new Class[0]);
    
    private static final LazyMethod getSupportedCipherSuites =
        new LazyMethod("Utilities", "getSupportedCipherSuites", new Class[0]);
    
    /**
     * The following need to be updated, eg anon not supported.
     */
    private static String ANONS1 = "TLS_DH_anon_WITH_AES_128_GCM_SHA256";
    private static String ANONS2 = "TLS_DH_anon_WITH_AES_256_GCM_SHA384";
    private static String ANONW1 = "TLS_DH_anon_WITH_AES_128_CBC_SHA";
    private static String ANONW2 = "TLS_ECDH_anon_WITH_RC4_128_SHA";
    private static String ANONW3 = "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA";
    private static String CONFS1 = "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256";
    private static String CONFS2 = "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256";
    private static String CONFS3 = "TLS_AES_128_GCM_SHA256";
    private static String CONFS4 = "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384";
    private static String CONFW1 = "TLS_DHE_DSS_WITH_AES_128_CBC_SHA";
    private static String CONFW2 = "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256";
    private static String CONFW3 = "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA";
    private static String CONFW4 = "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA";
    private static String NCONF1 = "TLS_ECDHE_ECDSA_WITH_NULL_SHA";
    private static String NCONF2 = "TLS_ECDHE_RSA_WITH_NULL_SHA";

    private static Principal AN = null;
    private static Principal CL = x500("CN=Client");
    private static Principal SV = x500("CN=Server");

    private static boolean INTEGRITY = true;
    private static boolean NO_INTEGRITY = false;

    static class WhichSide extends Enum {
	WhichSide(String name) { super(name); }
    }
    private static WhichSide CL_SIDE = new WhichSide("CL_SIDE");
    private static WhichSide SV_SIDE = new WhichSide("SV_SIDE");

    private static InvocationConstraints EMPTY = InvocationConstraints.EMPTY;

    private static InvocationConstraints TEST_CONSTRAINT =
	requirements(new TestConstraint());

    private static InvocationConstraints SV_AUTH_NO =
	requirements(ServerAuthentication.NO);
    private static InvocationConstraints SV_AUTH_YES =
	requirements(ServerAuthentication.YES);

    private static InvocationConstraints CL_AUTH_NO =
	requirements(ClientAuthentication.NO);
    private static InvocationConstraints CL_AUTH_YES =
	requirements(ClientAuthentication.YES);

    private static InvocationConstraints INT_NO = requirements(Integrity.NO);
    private static InvocationConstraints INT_YES = requirements(Integrity.YES);

    private static InvocationConstraints CONF_NO =
	requirements(Confidentiality.NO);
    private static InvocationConstraints CONF_YES =
	requirements(Confidentiality.YES);

    private static InvocationConstraints CONF_NO_WEAK =
	requirements(Confidentiality.NO, ConfidentialityStrength.WEAK);
    private static InvocationConstraints CONF_NO_STRONG =
	requirements(Confidentiality.NO, ConfidentialityStrength.STRONG);
    private static InvocationConstraints CONF_YES_WEAK =
	requirements(Confidentiality.YES, ConfidentialityStrength.WEAK);
    private static InvocationConstraints CONF_YES_STRONG =
	requirements(Confidentiality.YES, ConfidentialityStrength.STRONG);
    private static InvocationConstraints CONF_WEAK =
	requirements(ConfidentialityStrength.WEAK);
    private static InvocationConstraints CONF_STRONG =
	requirements(ConfidentialityStrength.STRONG);

    private static InvocationConstraints CL_AUTH_NO_DELEG_NO =
	requirements(ClientAuthentication.NO, Delegation.NO);
    private static InvocationConstraints CL_AUTH_NO_DELEG_YES =
	requirements(ClientAuthentication.NO, Delegation.YES);
    private static InvocationConstraints CL_AUTH_YES_DELEG_NO =
	requirements(ClientAuthentication.YES, Delegation.NO);
    private static InvocationConstraints CL_AUTH_YES_DELEG_YES =
	requirements(ClientAuthentication.YES, Delegation.YES);
    private static InvocationConstraints DELEG_NO = requirements(Delegation.NO);
    private static InvocationConstraints DELEG_YES =
	requirements(Delegation.YES);

    private static InvocationConstraints DELEG_ABS =
	requirements(new DelegationAbsoluteTime(1, 2, 3, 4));
    private static InvocationConstraints DELEG_REL =
	requirements(new DelegationRelativeTime(5, 6, 7, 8));

    private static InvocationConstraints CL_AUTH_NO_CL_MIN_TY_BAD =
	requirements(ClientAuthentication.NO,
		     new ClientMinPrincipalType(TestPrincipal.class));
    private static InvocationConstraints CL_AUTH_YES_CL_MIN_TY_BAD =
	requirements(ClientAuthentication.YES,
		     new ClientMinPrincipalType(
			 new Class[] {
			     X500Principal.class, TestPrincipal.class }));
    private static InvocationConstraints CL_AUTH_YES_CL_MIN_TY_GOOD =
	requirements(ClientAuthentication.YES,
		     new ClientMinPrincipalType(X500Principal.class));
    private static InvocationConstraints CL_MIN_TY_BAD =
	requirements(
	    new ClientMinPrincipalType(
		new Class[] {
		    TestPrincipal.class, X500Principal.class
		}));
    private static InvocationConstraints CL_MIN_TY_GOOD =
	requirements(new ClientMinPrincipalType(X500Principal.class));

    private static InvocationConstraints CL_AUTH_NO_CL_MAX_TY_BAD =
	requirements(ClientAuthentication.NO,
		     new ClientMaxPrincipalType(TestPrincipal.class));
    private static InvocationConstraints CL_AUTH_YES_CL_MAX_TY_BAD =
	requirements(ClientAuthentication.YES,
		     new ClientMaxPrincipalType(TestPrincipal.class));
    private static InvocationConstraints CL_AUTH_YES_CL_MAX_TY_GOOD =
	requirements(ClientAuthentication.YES,
		     new ClientMaxPrincipalType(
			 new Class[] {
			     X500Principal.class, TestPrincipal.class
			 }));
    private static InvocationConstraints CL_MAX_TY_BAD =
	requirements(new ClientMaxPrincipalType(TestPrincipal.class));
    private static InvocationConstraints CL_MAX_TY_GOOD =
	requirements(new ClientMaxPrincipalType(X500Principal.class));

    private static InvocationConstraints CL_AUTH_NO_CL_MIN_BAD =
	requirements(ClientAuthentication.NO, minPrincipals(SV));
    private static InvocationConstraints CL_AUTH_NO_CL_MIN_GOOD =
	requirements(ClientAuthentication.NO, minPrincipals(CL));
    private static InvocationConstraints CL_AUTH_YES_CL_MIN_BAD1 =
	requirements(ClientAuthentication.YES, minPrincipals(SV));
    private static InvocationConstraints CL_AUTH_YES_CL_MIN_BAD2 =
	requirements(ClientAuthentication.YES,
		     minPrincipals(new TestPrincipal("Foo")));
    private static InvocationConstraints CL_AUTH_YES_CL_MIN_GOOD =
	requirements(ClientAuthentication.YES, minPrincipals(CL));
    private static InvocationConstraints CL_MIN_BAD =
	requirements(minPrincipals(CL, SV));
    private static InvocationConstraints CL_MIN_GOOD =
	requirements(minPrincipals(CL));

    private static InvocationConstraints CL_AUTH_NO_CL_MAX_BAD =
	requirements(ClientAuthentication.NO, maxPrincipals(SV));
    private static InvocationConstraints CL_AUTH_NO_CL_MAX_GOOD =
	requirements(ClientAuthentication.NO, maxPrincipals(CL));
    private static InvocationConstraints CL_AUTH_YES_CL_MAX_BAD1 =
	requirements(ClientAuthentication.YES, maxPrincipals(SV));
    private static InvocationConstraints CL_AUTH_YES_CL_MAX_BAD2 =
	requirements(ClientAuthentication.YES,
		     maxPrincipals(new TestPrincipal("Foo")));
    private static InvocationConstraints CL_AUTH_YES_CL_MAX_GOOD1 =
	requirements(ClientAuthentication.YES, maxPrincipals(CL));
    private static InvocationConstraints CL_AUTH_YES_CL_MAX_GOOD2 =
	requirements(ClientAuthentication.YES, maxPrincipals(CL, SV));
    private static InvocationConstraints CL_AUTH_YES_CL_MAX_GOOD3 =
	requirements(ClientAuthentication.YES,
		     maxPrincipals(CL, SV, new TestPrincipal("Foo")));
    private static InvocationConstraints CL_MAX_BAD =
	requirements(maxPrincipals(new TestPrincipal("Foo")));
    private static InvocationConstraints CL_MAX_GOOD =
	requirements(maxPrincipals(CL));

    private static InvocationConstraints SV_AUTH_NO_SV_MIN_BAD =
	requirements(ServerAuthentication.NO, serverPrincipals(CL));
    private static InvocationConstraints SV_AUTH_NO_SV_MIN_GOOD =
	requirements(ServerAuthentication.NO, serverPrincipals(SV));
    private static InvocationConstraints SV_AUTH_YES_SV_MIN_BAD1 =
	requirements(ServerAuthentication.YES, serverPrincipals(CL));
    private static InvocationConstraints SV_AUTH_YES_SV_MIN_BAD2 =
	requirements(ServerAuthentication.YES,
		     serverPrincipals(new TestPrincipal("Foo")));
    private static InvocationConstraints SV_AUTH_YES_SV_MIN_BAD3 =
	requirements(ServerAuthentication.YES, serverPrincipals(CL, SV));
    private static InvocationConstraints SV_AUTH_YES_SV_MIN_GOOD =
	requirements(ServerAuthentication.YES, serverPrincipals(SV));
    private static InvocationConstraints SV_MIN_BAD =
	requirements(serverPrincipals(new TestPrincipal("Foo")));
    private static InvocationConstraints SV_MIN_GOOD =
	requirements(serverPrincipals(SV));

    private static InvocationConstraints CONN_ABS1 =
	requirements(new ConnectionAbsoluteTime(1));
    private static InvocationConstraints CONN_ABS2 =
	requirements(new ConnectionAbsoluteTime(2));

    private static InvocationConstraints CONN_REL =
	requirements(new ConnectionRelativeTime(1));

    private static boolean YES = true;
    private static boolean NO = false;

    static final Test[] tests = {
        /* Anonymous connections are no longer allowed due to MITM attack.
         * Changed have been made to tests below as per comments */

	/* Credentials */

	t(ANONS1, AN, AN, EMPTY, NO), //1 * Anon Changed to NO
	t(ANONS2, AN, SV, EMPTY, NO), //2
	t(ANONW1, CL, AN, EMPTY, NO), //3
	t(ANONW2, CL, SV, EMPTY, NO), //4
	t(CONFS1, AN, AN, EMPTY, NO), //5
	t(CONFS2, AN, SV, EMPTY, NO), //6 * Anon Changed to NO
	t(CONFS3, CL, AN, EMPTY, NO), //7 
	t(CONFS4, CL, SV, EMPTY, YES), //8

	/* TestConstraint */

	t(ANONS1, AN, AN, TEST_CONSTRAINT, NO), //9
	t(CONFW1, AN, SV, TEST_CONSTRAINT, NO), //10
	t(NCONF1, CL, SV, TEST_CONSTRAINT, NO), //11

	/* ServerAuthentication */

	t(ANONW1, AN, AN, SV_AUTH_NO,  NO), //12 * Anon Changed to NO
	t(ANONS1, AN, AN, SV_AUTH_YES, NO), //13
	t(CONFW1, AN, SV, SV_AUTH_NO,  NO), //14
	t(CONFW2, CL, SV, SV_AUTH_YES, YES), //15 Changed client to CL
	t(CONFW3, CL, SV, SV_AUTH_NO,  NO), //16
	t(CONFW4, CL, SV, SV_AUTH_YES, YES), //17

	/* ClientAuthentication */

	t(ANONS2, AN, AN, CL_AUTH_NO,  NO), //18 * Anon Changed to NO
	t(ANONW1, AN, AN, CL_AUTH_YES, NO), //19
	t(NCONF1, AN, SV, CL_AUTH_NO,  NO), //20 * Anon Changed to NO
	t(NCONF2, AN, SV, CL_AUTH_YES, NO), //21
	t(CONFS1, CL, SV, CL_AUTH_NO,  NO), //22
	t(CONFS2, CL, SV, CL_AUTH_YES, YES), //23

	/* Integrity */

	t(NCONF1, CL, SV, NO_INTEGRITY, EMPTY,   YES), //24 * Changed client to CL
	t(ANONS1, AN, AN, NO_INTEGRITY, INT_YES, NO), //26
	t(ANONS2, AN, AN, INTEGRITY,    EMPTY,   NO), //27
	t(ANONW1, AN, AN, INTEGRITY,    INT_NO,  NO), //28
	t(CONFS3, CL, SV, INTEGRITY,    INT_YES, YES), //29 * Changed from anon and null principals.

	/* Confidentiality */

	t(ANONW3, AN, AN, CONF_NO,  NO), //30
	t(ANONS1, AN, AN, CONF_YES, NO), //31 * Anon Changed to NO
	t(CONFS1, AN, SV, CONF_NO,  NO), //32
	t(CONFS2, CL, SV, CONF_YES, YES), //33 Changed client to CL
	t(CONFS3, CL, SV, CONF_NO,  NO), //34
	t(CONFS4, CL, SV, CONF_YES, YES), //35
	t(NCONF1, CL, SV, CONF_NO,  YES), //36 * Changed client to CL
	t(NCONF1, CL, SV, CONF_YES, NO), //37 * Changed client to CL
	t(CONFS3, CL, SV, CONF_NO,  NO), //38 * Cipher changed.
	t(CONFS3, CL, SV, CONF_YES, YES), //39

	/* ConfidentialityStrength */

	t(ANONS1, AN, AN, CONF_NO_WEAK,    NO), //40
	t(ANONS2, AN, AN, CONF_NO_STRONG,  NO), //41
	t(CONFS1, AN, SV, CONF_YES_WEAK,   NO), //42
	t(CONFS2, CL, SV, CONF_YES_STRONG, YES), //43
	t(CONFS3, AN, SV, CONF_WEAK,       NO), //44
	t(CONFS4, CL, SV, CONF_STRONG,     YES), //45

	t(ANONW1, AN, AN, CONF_NO_WEAK,    NO), //46
	t(ANONW2, AN, AN, CONF_NO_STRONG,  NO), //47
	t(CONFW1, CL, SV, CONF_YES_WEAK,   YES), //48 Changed client to CL
	t(CONFW2, CL, SV, CONF_YES_STRONG, NO), //49
	t(CONFW3, CL, SV, CONF_WEAK,       YES), //50 Changed client to CL
	t(CONFW4, CL, SV, CONF_STRONG,     NO), //51

	t(NCONF1, CL, SV, CONF_NO_WEAK,    NO), //52 * Changed client to CL. Conflicting constraints not supported. changed to NO.
	t(NCONF1, CL, SV, CONF_NO_STRONG,  NO), //53 * Conflicting constraints not supported. changed to NO.
	t(NCONF1, AN, SV, CONF_YES_WEAK,   NO), //54
	t(NCONF2, CL, SV, CONF_YES_STRONG, NO), //55 
	t(NCONF2, AN, SV, CONF_WEAK,       NO), //56 * Null client Changed to NO
	t(NCONF2, CL, SV, CONF_STRONG,     NO), //57 * Null confidentiality cipher, changed to NO.

	/* Delegation */

	t(ANONS1, AN, AN, CL_AUTH_NO_DELEG_NO,	  NO), //58 * Anon Changed to NO
	t(ANONS2, AN, AN, CL_AUTH_NO_DELEG_YES,	  NO), //59  Anon Changed to NO
	t(ANONW1, AN, AN, CL_AUTH_YES_DELEG_NO,	  NO), //60
	t(ANONW2, AN, AN, CL_AUTH_YES_DELEG_YES,  NO), //61
	t(ANONW3, AN, AN, DELEG_NO,		  NO), //62 * Anon Changed to NO
	t(ANONW3, AN, AN, DELEG_YES,		  NO), //63 Anon Changed to NO

	t(CONFS1, CL, SV, CL_AUTH_NO_DELEG_NO,	  NO), //64 * Changed client to CL
	t(CONFS2, CL, SV, CL_AUTH_NO_DELEG_YES,	  NO), //65 Changed client to CL
	t(CONFS3, CL, SV, CL_AUTH_YES_DELEG_NO,	  YES), //66 Changed client to CL
	t(CONFS4, CL, SV, CL_AUTH_YES_DELEG_YES,  NO), //67 Changed client to CL
	t(CONFW1, CL, SV, DELEG_NO,		  YES), //68 Changed client to CL
	t(CONFW2, CL, SV, DELEG_YES,		  NO), //69 Changed client to CL

	t(CONFW3, CL, SV, CL_AUTH_NO_DELEG_NO,	  NO), //70
	t(CONFW4, CL, SV, CL_AUTH_NO_DELEG_YES,	  NO), //71
	t(NCONF1, CL, SV, CL_AUTH_YES_DELEG_NO,	  YES), //72
	t(NCONF1, CL, SV, CL_AUTH_YES_DELEG_YES,  NO), //73
	t(NCONF2, CL, SV, DELEG_NO,		  YES), //74
	t(NCONF2, CL, SV, DELEG_YES,		  NO), //75 * Anon Changed to NO

	/* DelegationAbsoluteTime */

	t(CONFS3, CL, SV, CL_SIDE, DELEG_ABS, YES), //76 Changed principals from null.
	t(CONFW1, CL, SV, SV_SIDE, DELEG_ABS, YES), //77
	t(CONFS3, CL, SV, CL_SIDE, DELEG_ABS, YES), //78 * Changed cipher

	/* DelegationRelativeTime */

	t(CONFS3, AN, AN, SV_SIDE, DELEG_REL, NO), //79 * NO anon Changed Cipher
	t(CONFW1, AN, SV, CL_SIDE, DELEG_REL, NO), //80
	t(CONFS3, CL, SV, SV_SIDE, DELEG_REL, YES), //81 * Changed Cipher

	/* ClientMinPrincipalType */

	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MIN_TY_BAD,   NO), //82 Anon Changed to NO
	t(ANONW1, AN, AN, CL_AUTH_YES_CL_MIN_TY_BAD,  NO), //83
	t(ANONW2, AN, AN, CL_AUTH_YES_CL_MIN_TY_GOOD, NO), //84
	t(ANONW3, AN, AN, CL_MIN_TY_BAD,	      NO), //85 fail changed
	t(ANONW3, AN, AN, CL_MIN_TY_GOOD,	      NO), //86 * Anon Changed to NO

	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MIN_TY_BAD,   NO), //87 Anon Changed to NO
	t(CONFS3, AN, SV, CL_AUTH_YES_CL_MIN_TY_BAD,  NO), //88
	t(CONFS4, AN, SV, CL_AUTH_YES_CL_MIN_TY_GOOD, NO), //89
	t(CONFW1, CL, SV, CL_MIN_TY_BAD,	      NO), //90 changed client principal to CN.
	t(CONFW2, CL, SV, CL_MIN_TY_GOOD,	      YES), //91 changed client principal to CN.

	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MIN_TY_BAD,   NO), //92
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_TY_BAD,  NO), //93
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_TY_GOOD, YES), //94
	t(NCONF2, CL, SV, CL_MIN_TY_BAD,	      NO), //95
	t(NCONF2, CL, SV, CL_MIN_TY_GOOD,	      YES), //96

	/* ClientMaxPrincipalType */

	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MAX_TY_BAD,   NO), //97 Anon Changed to NO
	t(ANONW1, AN, AN, CL_AUTH_YES_CL_MAX_TY_BAD,  NO), //98
	t(ANONW2, AN, AN, CL_AUTH_YES_CL_MAX_TY_GOOD, NO), //99
	t(ANONW3, AN, AN, CL_MAX_TY_BAD,	      NO), //100 fail changed
	t(ANONW3, AN, AN, CL_MAX_TY_GOOD,	      NO), //101 * Anon Changed to NO

	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MAX_TY_BAD,   NO), //102 Anon Changed to NO
	t(CONFS3, AN, SV, CL_AUTH_YES_CL_MAX_TY_BAD,  NO), //103
	t(CONFS4, AN, SV, CL_AUTH_YES_CL_MAX_TY_GOOD, NO), //104
	t(CONFW1, AN, SV, CL_MAX_TY_BAD,              NO), //105 Anon Changed to NO
	t(CONFW2, CL, SV, CL_MAX_TY_GOOD,	      YES), //106 changed client principal to CN.

	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MAX_TY_BAD,   NO), //107
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_TY_BAD,  NO), //108
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_TY_GOOD, YES), //109
	t(NCONF2, CL, SV, CL_MAX_TY_BAD,              NO), //110
	t(NCONF2, CL, SV, CL_MAX_TY_GOOD,	      YES), //111 

	/* ClientMinPrincipal */

	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MIN_BAD,    NO), //112 Anon Changed to NO
	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MIN_GOOD,   NO), //113 Anon Changed to NO
	t(ANONW1, AN, AN, CL_AUTH_YES_CL_MIN_BAD1,  NO), //114
	t(ANONW2, AN, AN, CL_AUTH_YES_CL_MIN_GOOD,  NO), //115
	t(ANONW3, AN, AN, CL_MIN_BAD,		    NO), //116 Anon Changed to NO
	t(ANONW3, AN, AN, CL_MIN_GOOD,		    NO), //117 Anon Changed to NO

	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MIN_BAD,    NO), //118 Anon Changed to NO
	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MIN_GOOD,   NO), //119 Anon Changed to NO
	t(CONFS3, AN, SV, CL_AUTH_YES_CL_MIN_BAD2,  NO), //120
	t(CONFS4, AN, SV, CL_AUTH_YES_CL_MIN_GOOD,  NO), //121
	t(CONFW1, AN, SV, CL_MIN_BAD,		    NO), //122 Anon Changed to NO
	t(CONFW2, CL, SV, CL_MIN_GOOD,		    YES), //123 changed client from null to CL

	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MIN_BAD,    NO), //124
	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MIN_GOOD,   NO), //125
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_BAD1,  NO), //126
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_BAD2,  NO), //127
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_GOOD,  YES), //128
	t(NCONF2, CL, SV, CL_MIN_BAD,		    NO), //129
	t(NCONF2, CL, SV, CL_MIN_GOOD,		    YES), //130

	/* ClientMaxPrincipal */

	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MAX_BAD,    NO), //131 Anon Changed to NO
	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MAX_GOOD,   NO), //132 Anon Changed to NO
	t(ANONW1, AN, AN, CL_AUTH_YES_CL_MAX_BAD1,  NO), //133
	t(ANONW2, AN, AN, CL_AUTH_YES_CL_MAX_GOOD1, NO), //134
	t(ANONW3, AN, AN, CL_MAX_BAD,		    NO), //135 Anon Changed to NO
	t(ANONW3, AN, AN, CL_MAX_GOOD,		    NO), //136 Anon Changed to NO

	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MAX_BAD,    NO), //137 Anon Changed to NO
	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MAX_GOOD,   NO), //138 Anon Changed to NO
	t(CONFS3, AN, SV, CL_AUTH_YES_CL_MAX_BAD2,  NO), //139
	t(CONFS4, AN, SV, CL_AUTH_YES_CL_MAX_GOOD2, NO), //140
	t(CONFW1, AN, SV, CL_MAX_BAD,		    NO), //141 Anon Changed to NO
	t(CONFW2, CL, SV, CL_MAX_GOOD,		    YES), //142 changed client from  null to CL

	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MAX_BAD,    NO), //143
	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MAX_GOOD,   NO), //144
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_BAD1,  NO), //145
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_BAD2,  NO), //146
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_GOOD1, YES), //147
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_GOOD2, YES), //148
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_GOOD3, YES), //149
	t(NCONF2, CL, SV, CL_MAX_BAD,		    NO), //150
	t(NCONF2, CL, SV, CL_MAX_GOOD,		    YES), //151

	/* ServerMinPrincipal */

	t(ANONS1, AN, AN, SV_AUTH_NO_SV_MIN_BAD,    NO), //152 Anon Changed to NO
	t(ANONS1, AN, AN, SV_AUTH_NO_SV_MIN_GOOD,   NO), //153 Anon Changed to NO
	t(ANONW1, AN, AN, SV_AUTH_YES_SV_MIN_BAD1,  NO), //154
	t(ANONW2, AN, AN, SV_AUTH_YES_SV_MIN_GOOD,  NO), //155
	t(ANONW3, AN, AN, SV_MIN_BAD,		    NO), //156 Anon Changed to NO
	t(ANONW3, AN, AN, SV_MIN_GOOD,		    NO), //157 Anon Changed to NO

	t(CONFS1, CL, SV, SV_AUTH_NO_SV_MIN_BAD,    NO), //158 Changed client to CL
	t(CONFS1, CL, SV, SV_AUTH_NO_SV_MIN_GOOD,   NO), //159 Changed client to CL
	t(CONFS3, CL, SV, SV_AUTH_YES_SV_MIN_BAD1,  NO), //160 Changed client to CL
	t(CONFS3, CL, SV, SV_AUTH_YES_SV_MIN_BAD2,  NO), //161 Changed client to CL
	t(CONFS3, CL, SV, SV_AUTH_YES_SV_MIN_BAD3,  NO), //162
	t(CONFS4, CL, SV, SV_AUTH_YES_SV_MIN_GOOD,  YES), //163
	t(CONFW1, CL, SV, SV_MIN_BAD,		    NO), //164
	t(CONFW2, CL, SV, SV_MIN_GOOD,		    YES), //165

	/* ConnectionAbsoluteTime */

	t(CONFS3, CL, SV, CL_SIDE, CONN_ABS1, 1L), //166
	t(CONFW1, CL, SV, SV_SIDE, CONN_ABS2, 2L), // 167
	t(NCONF1, CL, SV, CL_SIDE, CONN_ABS1, 1L), // 168

	/* ConnectionRelativeTime */

	t(ANONS1, AN, AN, CL_SIDE, CONN_REL, NO), // 169
	t(ANONS1, AN, AN, SV_SIDE, CONN_REL, NO), // 170 Anon Changed to NO
	t(CONFW1, AN, SV, CL_SIDE, CONN_REL, NO), // 171
	t(CONFW1, CL, SV, SV_SIDE, CONN_REL, YES), // 172 Changed client to CL
	t(NCONF1, CL, SV, CL_SIDE, CONN_REL, NO), // 173
	t(CONFS3, CL, SV, SV_SIDE, CONN_REL, YES), // 174 Changed cipher

	/* ConstraintAlternatives */

	/* Heterogeneous */
	t(CONFS3, CL, SV, //175
	  requirements(alternatives(Integrity.YES, ServerAuthentication.NO)),
	  NO),
	t(ANONS1, AN, AN, //176
	  requirements(alternatives(minPrincipals(CL), maxPrincipals(CL))),
	  NO),
	/* Integrity */ 
	t(CONFS3, CL, SV, INTEGRITY,// 177
	  requirements(alternatives(Integrity.YES, Integrity.NO)),
	  YES),
	/* Connection time */
	t(CONFS3, CL, SV, CL_SIDE, // 178
	  requirements(alternatives(new ConnectionAbsoluteTime(1),
				    new ConnectionAbsoluteTime(2))),
	  2L),
	t(CONFS3, CL, SV, CL_SIDE, // 179
	  requirements(new ConnectionAbsoluteTime(1),
		       new ConnectionAbsoluteTime(2)),
	  1L),
	t(CONFS3, CL, SV, CL_SIDE, // 180
	  requirements(new ConnectionAbsoluteTime(1),
		       new ConnectionAbsoluteTime(2)),
	  1L),

	/* Preferences */

	t(CONFS3, CL, SV, // 181  These preferences are not supported.
	  preferences(ServerAuthentication.NO, ClientAuthentication.NO),
	  0),
	t(CONFS3, CL, SV, // 182
	  preferences(alternatives(ServerAuthentication.NO,
				   ServerAuthentication.YES)), // Only YES supported.
	  1),
	t(CONFS3, CL, SV, // 183 Neither pref supported.
	  preferences(alternatives(ServerAuthentication.NO,
				   ClientAuthentication.NO)),
	  0),
	t(CONFS3, CL, SV, //184  Only client auth pref supported.
	  preferences(ServerAuthentication.NO, ClientAuthentication.YES),
	  1),
	t(CONFW1, CL, SV, //185
	  preferences(serverPrincipals(SV)),
	  1),
	t(CONFW1, CL, SV, //186
	  preferences(serverPrincipals(SV), serverPrincipals(CL)),
	  1),
	t(CONFW1, CL, SV, // 187
	  preferences(alternatives(serverPrincipals(SV), serverPrincipals(CL))),
	  1)
    };

    private final String name;
    private final boolean nonNull;
    private final String cipherSuite;
    private final Principal client;
    private final Principal server;
    private final boolean integrity;
    private final boolean clientSide;
    private final InvocationConstraints constraints;
    private final long connectionTime;
    private final int preferences;

    private static Test t(String cipherSuite,
			  Principal client,
			  Principal server,
			  InvocationConstraints constraints,
			  boolean result)
    {
	return new TestConnectionContext(
	    cipherSuite, client, server, false /* integrity */,
	    true /* clientSide */, constraints, result, Long.MAX_VALUE, 0);
    }

    private static Test t(String cipherSuite,
			  Principal client,
			  Principal server,
			  boolean integrity,
			  InvocationConstraints constraints,
			  boolean result)
    {
	return new TestConnectionContext(
	    cipherSuite, client, server, integrity, true /* clientSide */,
	    constraints, result, Long.MAX_VALUE, 0);
    }

    private static Test t(String cipherSuite,
			  Principal client,
			  Principal server,
			  WhichSide whichSide,
			  InvocationConstraints constraints,
			  long connectionTime)
    {
	return new TestConnectionContext(
	    cipherSuite, client, server, false /* integrity */,
	    whichSide == CL_SIDE, constraints, true /* result */,
	    connectionTime, 0);
    }

    private static Test t(String cipherSuite,
			  Principal client,
			  Principal server,
			  InvocationConstraints constraints,
			  int preferences)
    {
	return new TestConnectionContext(
	    cipherSuite, client, server, false /* integrity */,
	    true /* clientSide */, constraints, true /* result */,
	    Long.MAX_VALUE, preferences);
    }
    private static Test t(String cipherSuite,
			  Principal client,
			  Principal server,
			  WhichSide whichSide,
			  InvocationConstraints constraints,
			  boolean result)
    {
	return new TestConnectionContext(
	    cipherSuite, client, server, false /* integrity */,
	    whichSide == CL_SIDE, constraints, result, Long.MAX_VALUE, 0);
    }

    private TestConnectionContext(String cipherSuite,
				  Principal client,
				  Principal server,
				  boolean integrity,
				  boolean clientSide,
				  InvocationConstraints constraints,
				  boolean result,
				  long connectionTime,
				  int preferences)
    {
	name = cipherSuite +
	    ", client = " + client +
	    ", server = " + server +
	    ", integrity = " + integrity +
	    ", clientSide = " + clientSide +
	    ", " + constraints;
	this.nonNull = result;
	this.cipherSuite = cipherSuite;
	this.client = client;
	this.server = server;
	this.integrity = integrity;
	this.clientSide = clientSide;
	this.constraints = constraints;
	this.connectionTime = connectionTime;
	this.preferences = preferences;
    }
    
    public static void main(String[] args) {
        System.out.println("Supported Ciphers");
        String [] ciphers = (String[]) getSupportedCipherSuites.invoke(
                null,
                new Object [] {}
        );
        System.out.println(Arrays.asList(ciphers));
	test(tests);
    }

    public String name() {
	return name;
    }

    public Object run() {
	return getInstance.invoke(
	    null,
	    new Object[] {
		cipherSuite, client, server, Boolean.valueOf(integrity),
		Boolean.valueOf(clientSide), constraints
	    });
    }

    public void check(Object result) throws Exception {
	if (nonNull != (result != null)) {
	    throw new FailedException(
		"Should be " + (nonNull ? "non-null" : "null"));
	}
	if (result != null) {
	    long contextConnectionTime =
		((Long) getConnectionTime.invoke(
		    result, new Object[0])).longValue();
	    if (connectionTime != contextConnectionTime) {
		throw new FailedException(
		    "Connection time should be " + connectionTime);
	    }
	    int contextPreferences =
	    ((Integer) getPreferences.invoke(
		result, new Object[0])).intValue();
	    if (preferences != contextPreferences) {
		throw new FailedException(
		    "Preferences should be " + preferences);
	    }
	}
    }
}
