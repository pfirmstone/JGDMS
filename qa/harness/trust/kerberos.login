
/* Kerberos login configurations */

org.apache.river.Reggie {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${reggie}";
};

org.apache.river.Mahalo {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${mahalo}";
};

org.apache.river.Outrigger {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${outrigger}";
};

org.apache.river.Mercury {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${mercury}";
};

org.apache.river.Norm {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${norm}";
};

org.apache.river.Phoenix {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${phoenix}";
};

org.apache.river.Test {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${test}";
};

org.apache.river.Fiddler {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${fiddler}";
};

org.apache.river.Group {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="${keytab}"
    useTicketCache=false
    storeKey=true
    debug=true
    principal="${group}";
};
