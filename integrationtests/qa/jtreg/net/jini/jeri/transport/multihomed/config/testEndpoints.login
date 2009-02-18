/* JAAS login configuration file for single principal */

onePrincipalServer {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/jgssTests.keytab"
	principal="testServer2@${java.security.krb5.realm}"
	storeKey=true
	doNotPrompt=true;
};
