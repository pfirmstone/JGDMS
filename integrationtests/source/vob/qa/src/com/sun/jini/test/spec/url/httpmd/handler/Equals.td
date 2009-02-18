testClass=Equals
testCategories=url_spec,url

# Equals Test Case names
Equals.testCases=equalsTheSame equalsAlgorithmCase equalsDigestCase equalsCommentsIgnore equalsCommentsIgnore2 equalsPort equalsDefaultPort equalsDefaultDiffPort equalsRef equalsQuery equalsHTTP equalsFILE equalsDiffHost equalsDiffPort equalsDiffAlgorithm equalsDiffAlgorithm2 equalsDiffAlgorithm3 equalsDiffAlgorithm4 equalsDiffDigest equalsDiffRef equalsDiffQuery

#        equalsTheSame
equalsTheSame.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsTheSame.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789

#        equalsAlgorithmCase
equalsAlgorithmCase.httpmdURL=httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
equalsAlgorithmCase.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789

#        equalsDigestCase
equalsDigestCase.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsDigestCase.anotherURL=httpmd://localhost/file.jar;MD5=ABCDEFabcdef0123456789

#        equalsCommentsIgnore
equalsCommentsIgnore.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsCommentsIgnore.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms

#        equalsCommentsIgnore2
equalsCommentsIgnore2.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms1
equalsCommentsIgnore2.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms2

#        equalsPort
equalsPort.httpmdURL=httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789
equalsPort.anotherURL=httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789

#       equalsDefaultPort 
equalsDefaultPort.httpmdURL=httpmd://localhost:80/file.jar;MD5=abcdefABCDEF0123456789
equalsDefaultPort.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789

#        equalsDefaultDiffPort
equalsDefaultDiffPort.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsDefaultDiffPort.anotherURL=httpmd://localhost:8001/file.jar;MD5=abcdefABCDEF0123456789
equalsDefaultDiffPort.expResult=false

#       equalsRef 
#equalsRef.httpmdURL=httpmd://localhost/file.jar;MD5=abc#Ref
# Harness workaround
equalsRef.httpmdURL=httpmd://localhost/file.jar;MD5=abc<PoundSign>Ref
#equalsRef.anotherURL=httpmd://localhost/file.jar;MD5=abc#Ref
# Harness workaround
equalsRef.anotherURL=httpmd://localhost/file.jar;MD5=abc<PoundSign>Ref

#        equalsQuery
equalsQuery.httpmdURL=httpmd://localhost/file.jar;MD5=abc?query
equalsQuery.anotherURL=httpmd://localhost/file.jar;MD5=abc?query

#        equalsHTTP
equalsHTTP.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsHTTP.anotherURL=http://localhost/file.jar
equalsHTTP.expResult=false

#        equalsFILE
equalsFILE.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsFILE.anotherURL=file:$/file.jar
equalsFILE.expResult=false

#        equalsDiffHost
equalsDiffHost.httpmdURL=httpmd://remotehost/file.jar;MD5=abcdefABCDEF0123456789
equalsDiffHost.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsDiffHost.expResult=false

#        equalsDiffPort
equalsDiffPort.httpmdURL=httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789
equalsDiffPort.anotherURL=httpmd://localhost:8001/file.jar;MD5=abcdefABCDEF0123456789
equalsDiffPort.expResult=false

#        equalsDiffAlgorithm
equalsDiffAlgorithm.httpmdURL=httpmd://localhost/file.jar;SHA=abcdefABCDEF0123456789
equalsDiffAlgorithm.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsDiffAlgorithm.expResult=false

#        equalsDiffAlgorithm2
equalsDiffAlgorithm2.httpmdURL=httpmd://localhost/file.jar;SHA=abcdefABCDEF0123456789
equalsDiffAlgorithm2.anotherURL=httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
equalsDiffAlgorithm2.expResult=false

#        equalsDiffAlgorithm3
equalsDiffAlgorithm3.httpmdURL=httpmd://localhost/file.jar;sha=abcdefABCDEF0123456789
equalsDiffAlgorithm3.anotherURL=httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
equalsDiffAlgorithm3.expResult=false

#        equalsDiffAlgorithm4
equalsDiffAlgorithm4.httpmdURL=httpmd://localhost/file.jar;sha=abcdefABCDEF0123456789
equalsDiffAlgorithm4.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
equalsDiffAlgorithm4.expResult=false

#        equalsDiffDigest
equalsDiffDigest.httpmdURL=httpmd://localhost/file.jar;MD5=abcdef
equalsDiffDigest.anotherURL=httpmd://localhost/file.jar;MD5=0123456789
equalsDiffDigest.expResult=false

#        equalsDiffRef
#equalsDiffRef.httpmdURL=httpmd://localhost/file.jar;MD5=abc#Ref1
# Harness workaround
equalsDiffRef.httpmdURL=httpmd://localhost/file.jar;MD5=abc<PoundSign>Ref1
#equalsDiffRef.anotherURL=httpmd://localhost/file.jar;MD5=abc#Ref2
# Harness workaround
equalsDiffRef.anotherURL=httpmd://localhost/file.jar;MD5=abc<PoundSign>Ref2
equalsDiffRef.expResult=false

#        equalsDiffQuery
equalsDiffQuery.httpmdURL=httpmd://localhost/file.jar;MD5=abc?query1
equalsDiffQuery.anotherURL=httpmd://localhost/file.jar;MD5=abc?query2
equalsDiffQuery.expResult=false

include0=../../url.properties
