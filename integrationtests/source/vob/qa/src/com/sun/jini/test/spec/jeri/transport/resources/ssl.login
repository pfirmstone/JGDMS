transport.SslClient {
    com.sun.security.auth.module.KeyStoreLoginModule required
    keyStoreURL="${keyStoreURL}"
    keyStorePasswordURL="${keyStorePasswordURL}"
    keyStoreAlias="client";
};

transport.SslServer {
    com.sun.security.auth.module.KeyStoreLoginModule required
    keyStoreURL="${keyStoreURL}"
    keyStorePasswordURL="${keyStorePasswordURL}"
    keyStoreAlias="server";
};

transport.Unauthorized {
    com.sun.security.auth.module.KeyStoreLoginModule required
    keyStoreURL="${keyStoreURL}"
    keyStorePasswordURL="${keyStorePasswordURL}"
    keyStoreAlias="unauthorized";
};