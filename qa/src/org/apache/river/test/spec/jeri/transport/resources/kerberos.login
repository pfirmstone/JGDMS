transport.KerberosClient {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${clientKeytabLocation}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${jeri.transport.kerberosClient}";
};

transport.KerberosServer {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${serverKeytabLocation}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${jeri.transport.kerberosServer}";
};