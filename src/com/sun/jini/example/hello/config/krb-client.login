/* JAAS login configuration file for Kerberos client */

com.sun.jini.example.hello.Client {
    com.sun.security.auth.module.Krb5LoginModule required
    storeKey=true;
};
