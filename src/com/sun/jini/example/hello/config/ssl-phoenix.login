/* JAAS login configuration file for Phoenix */

com.sun.jini.Phoenix {
    com.sun.security.auth.module.KeyStoreLoginModule required
	keyStoreAlias="phoenix"
	keyStoreURL="file:prebuiltkeys/phoenix.keystore"
	keyStorePasswordURL="file:prebuiltkeys/phoenix.password";
};

com.sun.jini.example.hello.Server {
    com.sun.security.auth.module.KeyStoreLoginModule required
	keyStoreAlias="server"
	keyStoreURL="file:prebuiltkeys/server.keystore"
	keyStorePasswordURL="file:prebuiltkeys/server.password";
};
