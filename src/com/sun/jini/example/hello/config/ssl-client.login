/* JAAS login configuration file for SSL client */

com.sun.jini.example.hello.Client {
    com.sun.security.auth.module.KeyStoreLoginModule required
	keyStoreAlias="client"
	keyStoreURL="file:prebuiltkeys/client.keystore";
};
