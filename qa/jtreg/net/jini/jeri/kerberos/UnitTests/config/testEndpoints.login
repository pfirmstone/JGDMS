/* JAAS login configuration file for client and server */

onePrincipalServer {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/jgssTests.keytab"
	principal="testServer2@${java.security.krb5.realm}"
	storeKey=true
	doNotPrompt=true;
};

twoPrincipalServer {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/jgssTests.keytab"
	principal="testServer1@${java.security.krb5.realm}"
	storeKey=true
	doNotPrompt=true;
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/jgssTests.keytab"
	principal="testServer2@${java.security.krb5.realm}"
	storeKey=true
	doNotPrompt=true;
};

testServer {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/jgssTests.keytab"
	principal="testServer1@${java.security.krb5.realm}"
	storeKey=true
	doNotPrompt=true;
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/jgssTests.keytab"
	principal="testServer2@${java.security.krb5.realm}"
	storeKey=true
	doNotPrompt=true;
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/jgssTests.keytab"
	principal="testServer3@${java.security.krb5.realm}"
	storeKey=true
	doNotPrompt=true;
};

testClient {
    com.sun.security.auth.module.Krb5LoginModule required
	useTicketCache=true
	ticketCache="config/testClient1.tgt"
	doNotPrompt=true;
    com.sun.security.auth.module.Krb5LoginModule required
	useTicketCache=true
	ticketCache="config/testClient2.tgt"
	doNotPrompt=true;
    com.sun.security.auth.module.Krb5LoginModule required
	useTicketCache=true
	ticketCache="config/testClient3.tgt"
	doNotPrompt=true;
};
