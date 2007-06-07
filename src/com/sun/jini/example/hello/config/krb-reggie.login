/* JAAS login configuration file for Reggie using Kerberos */

com.sun.jini.Reggie {
    com.sun.security.auth.module.Krb5LoginModule required 
	useKeyTab=true 
	keyTab="config/krb-servers.keytab" 
	storeKey=true 
	doNotPrompt=true 
	principal="${reggiePrincipal}";
};
