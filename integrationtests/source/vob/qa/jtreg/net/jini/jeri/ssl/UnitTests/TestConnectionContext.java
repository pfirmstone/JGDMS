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

    private static String ANONS1 = "SSL_DH_anon_WITH_RC4_128_MD5";
    private static String ANONS2 = "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA";
    private static String ANONW1 = "SSL_DH_anon_WITH_DES_CBC_SHA";
    private static String ANONW2 = "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5";
    private static String ANONW3 = "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA";
    private static String CONFS1 = "SSL_RSA_WITH_RC4_128_MD5";
    private static String CONFS2 = "SSL_RSA_WITH_RC4_128_SHA";
    private static String CONFS3 = "SSL_RSA_WITH_3DES_EDE_CBC_SHA";
    private static String CONFS4 = "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA";
    private static String CONFW1 = "SSL_RSA_WITH_DES_CBC_SHA";
    private static String CONFW2 = "SSL_DHE_DSS_WITH_DES_CBC_SHA";
    private static String CONFW3 = "SSL_RSA_EXPORT_WITH_RC4_40_MD5";
    private static String CONFW4 = "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA";
    private static String NCONF1 = "SSL_RSA_WITH_NULL_MD5";
    private static String NCONF2 = "SSL_RSA_WITH_NULL_SHA";

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

	/* Credentials */

	t(ANONS1, AN, AN, EMPTY, YES),
	t(ANONS2, AN, SV, EMPTY, NO),
	t(ANONW1, CL, AN, EMPTY, NO),
	t(ANONW2, CL, SV, EMPTY, NO),
	t(CONFS1, AN, AN, EMPTY, NO),
	t(CONFS2, AN, SV, EMPTY, YES),
	t(CONFS3, CL, AN, EMPTY, NO),
	t(CONFS4, CL, SV, EMPTY, YES),

	/* TestConstraint */

	t(ANONS1, AN, AN, TEST_CONSTRAINT, NO),
	t(CONFW1, AN, SV, TEST_CONSTRAINT, NO),
	t(NCONF1, CL, SV, TEST_CONSTRAINT, NO),

	/* ServerAuthentication */

	t(ANONW1, AN, AN, SV_AUTH_NO,  YES),
	t(ANONS1, AN, AN, SV_AUTH_YES, NO),
	t(CONFW1, AN, SV, SV_AUTH_NO,  NO),
	t(CONFW2, AN, SV, SV_AUTH_YES, YES),
	t(CONFW3, CL, SV, SV_AUTH_NO,  NO),
	t(CONFW4, CL, SV, SV_AUTH_YES, YES),

	/* ClientAuthentication */

	t(ANONS2, AN, AN, CL_AUTH_NO,  YES),
	t(ANONW1, AN, AN, CL_AUTH_YES, NO),
	t(NCONF1, AN, SV, CL_AUTH_NO,  YES),
	t(NCONF2, AN, SV, CL_AUTH_YES, NO),
	t(CONFS1, CL, SV, CL_AUTH_NO,  NO),
	t(CONFS2, CL, SV, CL_AUTH_YES, YES),

	/* Integrity */

	t(NCONF1, AN, SV, NO_INTEGRITY, EMPTY,   YES),
	t(NCONF2, AN, SV, NO_INTEGRITY, INT_NO,  NO),
	t(ANONS1, AN, AN, NO_INTEGRITY, INT_YES, NO),
	t(ANONS2, AN, AN, INTEGRITY,    EMPTY,   NO),
	t(ANONW1, AN, AN, INTEGRITY,    INT_NO,  NO),
	t(ANONW2, AN, AN, INTEGRITY,    INT_YES, YES),

	/* Confidentiality */

	t(ANONW3, AN, AN, CONF_NO,  NO),
	t(ANONS1, AN, AN, CONF_YES, YES),
	t(CONFS1, AN, SV, CONF_NO,  NO),
	t(CONFS2, AN, SV, CONF_YES, YES),
	t(CONFS3, CL, SV, CONF_NO,  NO),
	t(CONFS4, CL, SV, CONF_YES, YES),
	t(NCONF1, AN, SV, CONF_NO,  YES),
	t(NCONF1, AN, SV, CONF_YES, NO),
	t(NCONF2, CL, SV, CONF_NO,  YES),
	t(NCONF2, CL, SV, CONF_YES, NO),

	/* ConfidentialityStrength */

	t(ANONS1, AN, AN, CONF_NO_WEAK,    NO),
	t(ANONS2, AN, AN, CONF_NO_STRONG,  NO),
	t(CONFS1, AN, SV, CONF_YES_WEAK,   NO),
	t(CONFS2, CL, SV, CONF_YES_STRONG, YES),
	t(CONFS3, AN, SV, CONF_WEAK,       NO),
	t(CONFS4, CL, SV, CONF_STRONG,     YES),

	t(ANONW1, AN, AN, CONF_NO_WEAK,    NO),
	t(ANONW2, AN, AN, CONF_NO_STRONG,  NO),
	t(CONFW1, AN, SV, CONF_YES_WEAK,   YES),
	t(CONFW2, CL, SV, CONF_YES_STRONG, NO),
	t(CONFW3, AN, SV, CONF_WEAK,       YES),
	t(CONFW4, CL, SV, CONF_STRONG,     NO),

	t(NCONF1, AN, SV, CONF_NO_WEAK,    YES),
	t(NCONF1, CL, SV, CONF_NO_STRONG,  YES),
	t(NCONF1, AN, SV, CONF_YES_WEAK,   NO),
	t(NCONF2, CL, SV, CONF_YES_STRONG, NO),
	t(NCONF2, AN, SV, CONF_WEAK,       YES),
	t(NCONF2, CL, SV, CONF_STRONG,     YES),

	/* Delegation */

	t(ANONS1, AN, AN, CL_AUTH_NO_DELEG_NO,	  YES),
	t(ANONS2, AN, AN, CL_AUTH_NO_DELEG_YES,	  YES),
	t(ANONW1, AN, AN, CL_AUTH_YES_DELEG_NO,	  NO),
	t(ANONW2, AN, AN, CL_AUTH_YES_DELEG_YES,  NO),
	t(ANONW3, AN, AN, DELEG_NO,		  YES),
	t(ANONW3, AN, AN, DELEG_YES,		  YES),

	t(CONFS1, AN, SV, CL_AUTH_NO_DELEG_NO,	  YES),
	t(CONFS2, AN, SV, CL_AUTH_NO_DELEG_YES,	  YES),
	t(CONFS3, AN, SV, CL_AUTH_YES_DELEG_NO,	  NO),
	t(CONFS4, AN, SV, CL_AUTH_YES_DELEG_YES,  NO),
	t(CONFW1, AN, SV, DELEG_NO,		  YES),
	t(CONFW2, AN, SV, DELEG_YES,		  YES),

	t(CONFW3, CL, SV, CL_AUTH_NO_DELEG_NO,	  NO),
	t(CONFW4, CL, SV, CL_AUTH_NO_DELEG_YES,	  NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_DELEG_NO,	  YES),
	t(NCONF1, CL, SV, CL_AUTH_YES_DELEG_YES,  NO),
	t(NCONF2, CL, SV, DELEG_NO,		  YES),
	t(NCONF2, CL, SV, DELEG_YES,		  NO),

	/* DelegationAbsoluteTime */

	t(ANONS1, AN, AN, CL_SIDE, DELEG_ABS, YES),
	t(CONFW1, AN, SV, SV_SIDE, DELEG_ABS, YES),
	t(NCONF1, CL, SV, CL_SIDE, DELEG_ABS, YES),

	/* DelegationRelativeTime */

	t(ANONS1, AN, AN, SV_SIDE, DELEG_REL, YES),
	t(CONFW1, AN, SV, CL_SIDE, DELEG_REL, NO),
	t(NCONF1, CL, SV, SV_SIDE, DELEG_REL, YES),

	/* ClientMinPrincipalType */

	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MIN_TY_BAD,   YES),
	t(ANONW1, AN, AN, CL_AUTH_YES_CL_MIN_TY_BAD,  NO),
	t(ANONW2, AN, AN, CL_AUTH_YES_CL_MIN_TY_GOOD, NO),
	t(ANONW3, AN, AN, CL_MIN_TY_BAD,	      YES),
	t(ANONW3, AN, AN, CL_MIN_TY_GOOD,	      YES),

	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MIN_TY_BAD,   YES),
	t(CONFS3, AN, SV, CL_AUTH_YES_CL_MIN_TY_BAD,  NO),
	t(CONFS4, AN, SV, CL_AUTH_YES_CL_MIN_TY_GOOD, NO),
	t(CONFW1, AN, SV, CL_MIN_TY_BAD,	      YES),
	t(CONFW2, AN, SV, CL_MIN_TY_GOOD,	      YES),

	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MIN_TY_BAD,   NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_TY_BAD,  NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_TY_GOOD, YES),
	t(NCONF2, CL, SV, CL_MIN_TY_BAD,	      NO),
	t(NCONF2, CL, SV, CL_MIN_TY_GOOD,	      YES),

	/* ClientMaxPrincipalType */

	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MAX_TY_BAD,   YES),
	t(ANONW1, AN, AN, CL_AUTH_YES_CL_MAX_TY_BAD,  NO),
	t(ANONW2, AN, AN, CL_AUTH_YES_CL_MAX_TY_GOOD, NO),
	t(ANONW3, AN, AN, CL_MAX_TY_BAD,	      YES),
	t(ANONW3, AN, AN, CL_MAX_TY_GOOD,	      YES),

	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MAX_TY_BAD,   YES),
	t(CONFS3, AN, SV, CL_AUTH_YES_CL_MAX_TY_BAD,  NO),
	t(CONFS4, AN, SV, CL_AUTH_YES_CL_MAX_TY_GOOD, NO),
	t(CONFW1, AN, SV, CL_MAX_TY_BAD,	      YES),
	t(CONFW2, AN, SV, CL_MAX_TY_GOOD,	      YES),

	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MAX_TY_BAD,   NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_TY_BAD,  NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_TY_GOOD, YES),
	t(NCONF2, CL, SV, CL_MAX_TY_BAD,	      NO),
	t(NCONF2, CL, SV, CL_MAX_TY_GOOD,	      YES),

	/* ClientMinPrincipal */

	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MIN_BAD,    YES),
	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MIN_GOOD,   YES),
	t(ANONW1, AN, AN, CL_AUTH_YES_CL_MIN_BAD1,  NO),
	t(ANONW2, AN, AN, CL_AUTH_YES_CL_MIN_GOOD,  NO),
	t(ANONW3, AN, AN, CL_MIN_BAD,		    YES),
	t(ANONW3, AN, AN, CL_MIN_GOOD,		    YES),

	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MIN_BAD,    YES),
	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MIN_GOOD,   YES),
	t(CONFS3, AN, SV, CL_AUTH_YES_CL_MIN_BAD2,  NO),
	t(CONFS4, AN, SV, CL_AUTH_YES_CL_MIN_GOOD,  NO),
	t(CONFW1, AN, SV, CL_MIN_BAD,		    YES),
	t(CONFW2, AN, SV, CL_MIN_GOOD,		    YES),

	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MIN_BAD,    NO),
	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MIN_GOOD,   NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_BAD1,  NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_BAD2,  NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MIN_GOOD,  YES),
	t(NCONF2, CL, SV, CL_MIN_BAD,		    NO),
	t(NCONF2, CL, SV, CL_MIN_GOOD,		    YES),

	/* ClientMaxPrincipal */

	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MAX_BAD,    YES),
	t(ANONS1, AN, AN, CL_AUTH_NO_CL_MAX_GOOD,   YES),
	t(ANONW1, AN, AN, CL_AUTH_YES_CL_MAX_BAD1,  NO),
	t(ANONW2, AN, AN, CL_AUTH_YES_CL_MAX_GOOD1, NO),
	t(ANONW3, AN, AN, CL_MAX_BAD,		    YES),
	t(ANONW3, AN, AN, CL_MAX_GOOD,		    YES),

	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MAX_BAD,    YES),
	t(CONFS1, AN, SV, CL_AUTH_NO_CL_MAX_GOOD,   YES),
	t(CONFS3, AN, SV, CL_AUTH_YES_CL_MAX_BAD2,  NO),
	t(CONFS4, AN, SV, CL_AUTH_YES_CL_MAX_GOOD2, NO),
	t(CONFW1, AN, SV, CL_MAX_BAD,		    YES),
	t(CONFW2, AN, SV, CL_MAX_GOOD,		    YES),

	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MAX_BAD,    NO),
	t(CONFW3, CL, SV, CL_AUTH_NO_CL_MAX_GOOD,   NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_BAD1,  NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_BAD2,  NO),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_GOOD1, YES),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_GOOD2, YES),
	t(NCONF1, CL, SV, CL_AUTH_YES_CL_MAX_GOOD3, YES),
	t(NCONF2, CL, SV, CL_MAX_BAD,		    NO),
	t(NCONF2, CL, SV, CL_MAX_GOOD,		    YES),

	/* ServerMinPrincipal */

	t(ANONS1, AN, AN, SV_AUTH_NO_SV_MIN_BAD,    YES),
	t(ANONS1, AN, AN, SV_AUTH_NO_SV_MIN_GOOD,   YES),
	t(ANONW1, AN, AN, SV_AUTH_YES_SV_MIN_BAD1,  NO),
	t(ANONW2, AN, AN, SV_AUTH_YES_SV_MIN_GOOD,  NO),
	t(ANONW3, AN, AN, SV_MIN_BAD,		    YES),
	t(ANONW3, AN, AN, SV_MIN_GOOD,		    YES),

	t(CONFS1, AN, SV, SV_AUTH_NO_SV_MIN_BAD,    NO),
	t(CONFS1, AN, SV, SV_AUTH_NO_SV_MIN_GOOD,   NO),
	t(CONFS3, AN, SV, SV_AUTH_YES_SV_MIN_BAD1,  NO),
	t(CONFS3, AN, SV, SV_AUTH_YES_SV_MIN_BAD2,  NO),
	t(CONFS3, CL, SV, SV_AUTH_YES_SV_MIN_BAD3,  NO),
	t(CONFS4, CL, SV, SV_AUTH_YES_SV_MIN_GOOD,  YES),
	t(CONFW1, CL, SV, SV_MIN_BAD,		    NO),
	t(CONFW2, CL, SV, SV_MIN_GOOD,		    YES),

	/* ConnectionAbsoluteTime */

	t(ANONS1, AN, AN, CL_SIDE, CONN_ABS1, 1L),
	t(CONFW1, AN, SV, SV_SIDE, CONN_ABS2, 2L),
	t(NCONF1, CL, SV, CL_SIDE, CONN_ABS1, 1L),

	/* ConnectionRelativeTime */

	t(ANONS1, AN, AN, CL_SIDE, CONN_REL, NO),
	t(ANONS1, AN, AN, SV_SIDE, CONN_REL, YES),
	t(CONFW1, AN, SV, CL_SIDE, CONN_REL, NO),
	t(CONFW1, AN, SV, SV_SIDE, CONN_REL, YES),
	t(NCONF1, CL, SV, CL_SIDE, CONN_REL, NO),
	t(NCONF1, CL, SV, SV_SIDE, CONN_REL, YES),

	/* ConstraintAlternatives */

	/* Heterogeneous */
	t(ANONS1, AN, AN,
	  requirements(alternatives(Integrity.YES, ServerAuthentication.NO)),
	  NO),
	t(ANONS1, AN, AN,
	  requirements(alternatives(minPrincipals(CL), maxPrincipals(CL))),
	  NO),
	/* Integrity */
	t(ANONS1, AN, AN, INTEGRITY,
	  requirements(alternatives(Integrity.YES, Integrity.NO)),
	  YES),
	/* Connection time */
	t(ANONS1, AN, AN, CL_SIDE,
	  requirements(alternatives(new ConnectionAbsoluteTime(1),
				    new ConnectionAbsoluteTime(2))),
	  2L),
	t(ANONS1, AN, AN, CL_SIDE,
	  requirements(new ConnectionAbsoluteTime(1),
		       new ConnectionAbsoluteTime(2)),
	  1L),
	t(ANONS1, AN, AN, CL_SIDE,
	  requirements(new ConnectionAbsoluteTime(1),
		       new ConnectionAbsoluteTime(2)),
	  1L),

	/* Preferences */

	t(ANONS1, AN, AN,
	  preferences(ServerAuthentication.NO, ClientAuthentication.NO),
	  2),
	t(ANONS1, AN, AN,
	  preferences(alternatives(ServerAuthentication.NO,
				   ServerAuthentication.YES)),
	  1),
	t(ANONS1, AN, AN,
	  preferences(alternatives(ServerAuthentication.NO,
				   ClientAuthentication.NO)),
	  0),
	t(ANONS1, AN, AN,
	  preferences(ServerAuthentication.NO, ClientAuthentication.YES),
	  1),
	t(CONFW1, AN, SV,
	  preferences(serverPrincipals(SV)),
	  1),
	t(CONFW1, AN, SV,
	  preferences(serverPrincipals(SV), serverPrincipals(CL)),
	  1),
	t(CONFW1, AN, SV,
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
