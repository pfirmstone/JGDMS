/* JAAS login configuration file for Phoenix using Kerberos */

com.sun.jini.Phoenix {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/krb-servers.keytab" 
	storeKey=true 
	doNotPrompt=true 
	principal="${phoenixPrincipal}";
};

com.sun.jini.example.hello.Server {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/krb-servers.keytab" 
	storeKey=true 
	doNotPrompt=true 
	principal="${serverPrincipal}";
};
