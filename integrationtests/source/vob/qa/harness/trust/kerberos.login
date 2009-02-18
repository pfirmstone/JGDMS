
/* Kerberos login configurations */

com.sun.jini.Reggie {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${reggie}";
};

com.sun.jini.Mahalo {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${mahalo}";
};

com.sun.jini.Outrigger {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${outrigger}";
};

com.sun.jini.Mercury {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${mercury}";
};

com.sun.jini.Norm {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${norm}";
};

com.sun.jini.Phoenix {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${phoenix}";
};

com.sun.jini.Test {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${test}";
};

com.sun.jini.Fiddler {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${fiddler}";
};

com.sun.jini.Group {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${group}";
};
