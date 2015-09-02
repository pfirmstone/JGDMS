testClass=ParseURL
testCategories=url_spec,url

# ParseURL Test Case names
ParseURL.testCases=parseURLBadAbs.1 parseURLBadAbs.2 parseURLBadAbs.3 parseURLBadAbs.4 parseURLBadAbs.5 parseURLBadAbs.6 parseURLBadAbs.7 parseURLBadAbs.8 parseURLBadAbs.9 parseURLBadAbs.10 parseURLBadAbs.11 parseURLBadAbs.12 parseURLBadAbs.13 parseURLBadAbs.14 parseURLBadAbs.15 parseURLGoodAbs.1 parseURLGoodAbs.2 parseURLGoodAbs.3 parseURLGoodAbs.4 parseURLGoodAbs.5 parseURLGoodAbs.6 parseURLGoodAbs.7 parseURLGoodAbs.8 parseURLGoodAbs.9 parseURLGoodAbs.10 parseURLGoodAbs.11 parseURLGoodAbs.12 parseURLGoodAbs.13 parseURLBadRel.1 parseURLBadRel.2 parseURLBadRel.3 parseURLBadRel.4 parseURLBadRel.5 parseURLBadRel.6 parseURLBadRel.7 parseURLBadRel.8 parseURLBadRel.9 parseURLBadRel.10 parseURLBadRel.11 parseURLBadRel.12 parseURLBadRel.13 parseURLBadRel.14 parseURLBadRel.15 parseURLBadRel.16 parseURLBadRel.17 parseURLBadRel.18 parseURLGoodRel.1 parseURLGoodRel.2 parseURLGoodRel.3 parseURLGoodRel.4 parseURLGoodRel.5 parseURLGoodRel.6 parseURLGoodRel.7 parseURLGoodRel.8 parseURLGoodRel.9 parseURLGoodRel.10 parseURLGoodRel.11 parseURLGoodRel.12 parseURLGoodRel.13 parseURLGoodRel.14 parseURLGoodRel.15 parseURLGoodRel.16 parseURLGoodRel.17 parseURLGoodRel.18


##########        parseURLBadAbs :: Bad absolute syntax
parseURLBadAbs.1.Expected=java.net.MalformedURLException.class
parseURLBadAbs.10.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.11.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.12.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.13.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.14.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.15.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.2.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.3.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.4.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.5.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.6.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.7.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.8.Expected=${parseURLBadAbs.1.Expected}
parseURLBadAbs.9.Expected=${parseURLBadAbs.1.Expected}

#parseURLBadAbs.1.Spec=httpmd://foo:20/bar/baz?q#r
parseURLBadAbs.1.Spec=httpmd://foo:20/bar/baz?q<PoundSign>r

parseURLBadAbs.10.Spec=httpmd:baz?;md5=abcd

#parseURLBadAbs.11.Spec=httpmd:baz?q#;md5=abcd
parseURLBadAbs.11.Spec=httpmd:baz?q<PoundSign>;md5=abcd

parseURLBadAbs.12.Spec=httpmd:baz?q;md5=abcd,!

parseURLBadAbs.13.Spec=httpmd:,

parseURLBadAbs.14.Spec=httpmd:,comment

#parseURLBadAbs.15.Spec=httpmd://foo:20;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
parseURLBadAbs.15.Spec=httpmd://foo:20;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*<QuoteSign>():@&=+<DollarSign>

#parseURLBadAbs.2.Spec=httpmd://foo/bar/baz;?q#r
parseURLBadAbs.2.Spec=httpmd://foo/bar/baz;?q<PoundSign>r

#parseURLBadAbs.3.Spec=httpmd:/bar/baz;md5?q#r
parseURLBadAbs.3.Spec=httpmd:/bar/baz;md5?q<PoundSign>r

#parseURLBadAbs.4.Spec=httpmd:baz;md5=?q#r
parseURLBadAbs.4.Spec=httpmd:baz;md5=?q<PoundSign>r

#parseURLBadAbs.5.Spec=baz;md5=abxd#r
parseURLBadAbs.5.Spec=baz;md5=abxd<PoundSign>r

parseURLBadAbs.6.Spec=httpmd:baz;ugh=abcd

parseURLBadAbs.7.Spec=httpmd:baz;=

parseURLBadAbs.8.Spec=httpmd:baz;=abcd

parseURLBadAbs.9.Spec=httpmd:baz;md5=abcd;ugh=1234


##########        parseURLGoodAbs :: Good absolute syntax
#parseURLGoodAbs.1.Spec=HTTPMD://FOO:20/bar/baz;p1=v1;MD5=ABCD?q#r
parseURLGoodAbs.1.Spec=HTTPMD://FOO:20/bar/baz;p1=v1;MD5=ABCD?q<PoundSign>r
#parseURLGoodAbs.1.Expected=httpmd://FOO:20/bar/baz;p1=v1;MD5=ABCD?q#r
parseURLGoodAbs.1.Expected=httpmd://FOO:20/bar/baz;p1=v1;MD5=ABCD?q<PoundSign>r

parseURLGoodAbs.10.Spec=httpmd:baz;sha-1=99f6837808c0a79398bf69d83cfb1b82d20cf0cf
parseURLGoodAbs.10.Expected=httpmd:baz;sha-1=99f6837808c0a79398bf69d83cfb1b82d20cf0cf

parseURLGoodAbs.11.Spec=httpmd:;md5=1234
parseURLGoodAbs.11.Expected=httpmd:;md5=1234

#parseURLGoodAbs.12.Spec=httpmd://foo:20/bar/baz;sha=1234#r
parseURLGoodAbs.12.Spec=httpmd://foo:20/bar/baz;sha=1234<PoundSign>r
#parseURLGoodAbs.12.Expected=httpmd://foo:20/bar/baz;sha=1234#r
parseURLGoodAbs.12.Expected=httpmd://foo:20/bar/baz;sha=1234<PoundSign>r

#parseURLGoodAbs.13.Spec=httpmd://foo:20/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$;
parseURLGoodAbs.13.Spec=httpmd://foo:20/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*<QuoteSign>():@&=+<DollarSign>
#parseURLGoodAbs.13.Expected=httpmd://foo:20/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$;
parseURLGoodAbs.13.Expected=httpmd://foo:20/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*<QuoteSign>():@&=+<DollarSign>

parseURLGoodAbs.2.Spec=httpmd://foo:20/bar/baz;sha=1234?q
parseURLGoodAbs.2.Expected=httpmd://foo:20/bar/baz;sha=1234?q

parseURLGoodAbs.3.Spec=httpmd://foo:20/bar/baz;md5=1234
parseURLGoodAbs.3.Expected=httpmd://foo:20/bar/baz;md5=1234

parseURLGoodAbs.4.Spec=httpmd://foo/bar/baz;md5=1234
parseURLGoodAbs.4.Expected=httpmd://foo/bar/baz;md5=1234

parseURLGoodAbs.5.Spec=httpmd:/bar/baz;md5=1234
parseURLGoodAbs.5.Expected=httpmd:/bar/baz;md5=1234

#parseURLGoodAbs.6.Spec=httpmd:/bar/baz;md5=1234,?q#r
parseURLGoodAbs.6.Spec=httpmd:/bar/baz;md5=1234,?q<PoundSign>r
#parseURLGoodAbs.6.Expected=httpmd:/bar/baz;md5=1234,?q#r
parseURLGoodAbs.6.Expected=httpmd:/bar/baz;md5=1234,?q<PoundSign>r

#parseURLGoodAbs.7.Spec=httpmd:/bar/baz;md5=1234,Hello7-_.~*'():@&=+$,#r
parseURLGoodAbs.7.Spec=httpmd:/bar/baz;md5=1234,Hello7-_.~*<QuoteSign>():@&=+<DollarSign>,<PoundSign>r
#parseURLGoodAbs.7.Expected=httpmd:/bar/baz;md5=1234,Hello7-_.~*'():@&=+$,#r
parseURLGoodAbs.7.Expected=httpmd:/bar/baz;md5=1234,Hello7-_.~*<QuoteSign>():@&=+<DollarSign>,<PoundSign>r

parseURLGoodAbs.8.Spec=httpmd:/bar/baz;md5=1234,x*?q
parseURLGoodAbs.8.Expected=httpmd:/bar/baz;md5=1234,x*?q

parseURLGoodAbs.9.Spec=httpmd:/bar,baz;md5=1234,c1
parseURLGoodAbs.9.Expected=httpmd:/bar,baz;md5=1234,c1


##########        parseURLBadRel :: Bad relative syntax
parseURLBadRel.1.Expected=java.net.MalformedURLException.class
parseURLBadRel.10.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.11.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.12.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.13.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.14.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.15.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.16.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.17.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.18.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.2.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.3.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.4.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.5.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.6.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.7.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.8.Expected=${parseURLBadRel.1.Expected}
parseURLBadRel.9.Expected=${parseURLBadRel.1.Expected}

#parseURLBadRel.1.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.1.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLBadRel.1.Spec=//foo/bar/baz;?q#r
parseURLBadRel.1.Spec=//foo/bar/baz;?q<PoundSign>r

#parseURLBadRel.10.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.10.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
parseURLBadRel.10.Spec=baz

#parseURLBadRel.11.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.11.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
parseURLBadRel.11.Spec=baz,comment

parseURLBadRel.12.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.12.Spec=!bad-comment

#parseURLBadRel.13.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.13.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLBadRel.13.Spec=baz;sha=abcd,{?q#r
parseURLBadRel.13.Spec=baz;sha=abcd,{?q<PoundSign>r

#parseURLBadRel.14.Context=httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLBadRel.14.Context=httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
parseURLBadRel.14.Spec=c1?q1#r1

#parseURLBadRel.15.Context=httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLBadRel.15.Context=httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
parseURLBadRel.15.Spec=c1?q1

#parseURLBadRel.16.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLBadRel.16.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
#parseURLBadRel.16.Spec=c1#r1
parseURLBadRel.16.Spec=c1<PoundSign>r1

#parseURLBadRel.17.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLBadRel.17.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
parseURLBadRel.17.Spec=c1

#parseURLBadRel.18.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLBadRel.18.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
parseURLBadRel.18.Spec=?q1#r1

#parseURLBadRel.2.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.2.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLBadRel.2.Spec=/bar/baz;sha?q#r
parseURLBadRel.2.Spec=/bar/baz;sha?q<PoundSign>r

#parseURLBadRel.3.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.3.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLBadRel.3.Spec=/bar/baz;=?q#r
parseURLBadRel.3.Spec=/bar/baz;=?q<PoundSign>r

#parseURLBadRel.4.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.4.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLBadRel.4.Spec=baz;sha=?q#r
parseURLBadRel.4.Spec=baz;sha=?q<PoundSign>r

#parseURLBadRel.5.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.5.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLBadRel.5.Spec=baz;sha=abxd#r
parseURLBadRel.5.Spec=baz;sha=abxd<PoundSign>r

#parseURLBadRel.6.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.6.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
parseURLBadRel.6.Spec=baz;ugh=abcd

#parseURLBadRel.7.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.7.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
parseURLBadRel.7.Spec=baz;sha=abcd;ugh=1234

#parseURLBadRel.8.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.8.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
parseURLBadRel.8.Spec=baz?;sha=abcd

#parseURLBadRel.9.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLBadRel.9.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLBadRel.9.Spec=baz?q#;sha=abcd
parseURLBadRel.9.Spec=baz?q<PoundSign>;sha=abcd


##########        parseURLGoodRel :: Good relative syntax
#parseURLGoodRel.1.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q2#r2
parseURLGoodRel.1.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q2<PoundSign>r2
#parseURLGoodRel.1.Spec=httpmd://foo:30/bar/baz;sha=1234,c2?q#r
parseURLGoodRel.1.Spec=httpmd://foo:30/bar/baz;sha=1234,c2?q<PoundSign>r
#parseURLGoodRel.1.Expected=httpmd://foo:30/bar/baz;sha=1234,c2?q#r
parseURLGoodRel.1.Expected=httpmd://foo:30/bar/baz;sha=1234,c2?q<PoundSign>r

#parseURLGoodRel.10.Context=httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLGoodRel.10.Context=httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
parseURLGoodRel.10.Spec=,c1?q1
parseURLGoodRel.10.Expected=httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c1?q1

#parseURLGoodRel.11.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLGoodRel.11.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
#parseURLGoodRel.11.Spec=,c1#r1
parseURLGoodRel.11.Spec=,c1<PoundSign>r1
#parseURLGoodRel.11.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c1#r1
parseURLGoodRel.11.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c1<PoundSign>r1

#parseURLGoodRel.12.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLGoodRel.12.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
parseURLGoodRel.12.Spec=,c1
parseURLGoodRel.12.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c1

#parseURLGoodRel.13.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLGoodRel.13.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
parseURLGoodRel.13.Spec=,
parseURLGoodRel.13.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,

parseURLGoodRel.14.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
#parseURLGoodRel.14.Spec=,?q1#r1
parseURLGoodRel.14.Spec=,?q1<PoundSign>r1
#parseURLGoodRel.14.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,?q1#r1
parseURLGoodRel.14.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,?q1<PoundSign>r1

parseURLGoodRel.15.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
#parseURLGoodRel.15.Spec=#r1
parseURLGoodRel.15.Spec=<PoundSign>r1
#parseURLGoodRel.15.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r1
parseURLGoodRel.15.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r1

parseURLGoodRel.16.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
#parseURLGoodRel.16.Spec=,#r1
parseURLGoodRel.16.Spec=,<PoundSign>r1
#parseURLGoodRel.16.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,#r1
parseURLGoodRel.16.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,<PoundSign>r1

parseURLGoodRel.17.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
#parseURLGoodRel.17.Spec=gamma;p2=v2;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
parseURLGoodRel.17.Spec=gamma;p2=v2;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*<QuoteSign>():@&=+<DollarSign>
#parseURLGoodRel.17.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
parseURLGoodRel.17.Expected=httpmd://alpha/beta/gamma;p2=v2;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*<QuoteSign>():@&=+<DollarSign>

parseURLGoodRel.18.Context=httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
#parseURLGoodRel.18.Spec=file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
parseURLGoodRel.18.Spec=file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*<QuoteSign>():@&=+<DollarSign>
#parseURLGoodRel.18.Expected=httpmd://alpha/beta/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
parseURLGoodRel.18.Expected=httpmd://alpha/beta/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*<QuoteSign>():@&=+<DollarSign>

#parseURLGoodRel.2.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q2#r2
parseURLGoodRel.2.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q2<PoundSign>r2
parseURLGoodRel.2.Spec=httpmd://foo:30/bar/baz;a=b,c;sha=1234?q
parseURLGoodRel.2.Expected=httpmd://foo:30/bar/baz;a=b,c;sha=1234?q

#parseURLGoodRel.3.Context=httpmd://alpha:20/beta/gamma;p2=v1,2;md5=abcd?q2#r2
parseURLGoodRel.3.Context=httpmd://alpha:20/beta/gamma;p2=v1,2;md5=abcd?q2<PoundSign>r2
#parseURLGoodRel.3.Spec=//foo:30/bar/baz;sha=1234,c2#r
parseURLGoodRel.3.Spec=//foo:30/bar/baz;sha=1234,c2<PoundSign>r
#parseURLGoodRel.3.Expected=httpmd://foo:30/bar/baz;sha=1234,c2#r
parseURLGoodRel.3.Expected=httpmd://foo:30/bar/baz;sha=1234,c2<PoundSign>r

#parseURLGoodRel.4.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLGoodRel.4.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
parseURLGoodRel.4.Spec=//foo:30/bar/baz;sha=1234
parseURLGoodRel.4.Expected=httpmd://foo:30/bar/baz;sha=1234

#parseURLGoodRel.5.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLGoodRel.5.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLGoodRel.5.Spec=/bar/baz;sha=1234?q#r
parseURLGoodRel.5.Spec=/bar/baz;sha=1234?q<PoundSign>r
#parseURLGoodRel.5.Expected=httpmd://alpha:20/bar/baz;sha=1234?q#r
parseURLGoodRel.5.Expected=httpmd://alpha:20/bar/baz;sha=1234?q<PoundSign>r

#parseURLGoodRel.6.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLGoodRel.6.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLGoodRel.6.Spec=baz;sha=1234?q#r
parseURLGoodRel.6.Spec=baz;sha=1234?q<PoundSign>r
#parseURLGoodRel.6.Expected=httpmd://alpha:20/beta/baz;sha=1234?q#r
parseURLGoodRel.6.Expected=httpmd://alpha:20/beta/baz;sha=1234?q<PoundSign>r

#parseURLGoodRel.7.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLGoodRel.7.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLGoodRel.7.Spec=baz;sha=1234?q#r
parseURLGoodRel.7.Spec=baz;sha=1234?q<PoundSign>r
#parseURLGoodRel.7.Expected=httpmd://alpha:20/beta/baz;sha=1234?q#r
parseURLGoodRel.7.Expected=httpmd://alpha:20/beta/baz;sha=1234?q<PoundSign>r

#parseURLGoodRel.8.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
parseURLGoodRel.8.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2<PoundSign>r2
#parseURLGoodRel.8.Spec=;sha=1234?q#r
parseURLGoodRel.8.Spec=;sha=1234?q<PoundSign>r
#parseURLGoodRel.8.Expected=httpmd://alpha:20/beta/;sha=1234?q#r
parseURLGoodRel.8.Expected=httpmd://alpha:20/beta/;sha=1234?q<PoundSign>r

#parseURLGoodRel.9.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
parseURLGoodRel.9.Context=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c2?q2<PoundSign>r2
#parseURLGoodRel.9.Spec=,c1?q1#r1
parseURLGoodRel.9.Spec=,c1?q1<PoundSign>r1
#parseURLGoodRel.9.Expected=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q1#r1
parseURLGoodRel.9.Expected=httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q1<PoundSign>r1
include0=../../url.properties
