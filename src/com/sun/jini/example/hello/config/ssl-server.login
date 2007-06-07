/* JAAS login configuration file for server */

com.sun.jini.example.hello.Server {
    com.sun.security.auth.module.KeyStoreLoginModule required
	keyStoreAlias="server"
	keyStoreURL="file:prebuiltkeys/server.keystore"
	keyStorePasswordURL="file:prebuiltkeys/server.password";
};
