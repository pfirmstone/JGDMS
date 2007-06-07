/* JAAS login configuration file for Reggie */

com.sun.jini.Reggie {
    com.sun.security.auth.module.KeyStoreLoginModule required
	keyStoreAlias="reggie"
	keyStoreURL="file:prebuiltkeys/reggie.keystore"
	keyStorePasswordURL="file:prebuiltkeys/reggie.password";
};

