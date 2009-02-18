/* JAAS login configuration file for server */
testServer {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/jgssTests.keytab"
	principal="testServer1@${java.security.krb5.realm}"
	storeKey=true
	doNotPrompt=true;
};
