e2etest.KerberosClient {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${clientKeytabLocation}"
    useTicketCache=true
    debug=true
    principal="${kerberosClient}";
};

e2etest.KerberosServer {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${serverKeytabLocation}"
    useTicketCache=true
    storeKey=true
    debug=true
    principal="${kerberosServer}";
};