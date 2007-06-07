/* JAAS login configuration file for server */

com.sun.jini.example.hello.Server {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/krb-servers.keytab" 
	storeKey=true 
	doNotPrompt=true 
	principal="${serverPrincipal}";
};
