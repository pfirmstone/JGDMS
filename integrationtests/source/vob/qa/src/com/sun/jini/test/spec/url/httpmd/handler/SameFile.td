testClass=SameFile
testCategories=url_spec,url

# SameFile Test Case names
SameFile.testCases=sameFileTheSame sameFileAlgorithmCase sameFileDigestCase sameFileCommentsIgnore sameFileCommentsIgnore2 sameFilePort sameFileDefaultPort sameFileDefaultDiffPort sameFileRef sameFileQuery sameFileHTTP sameFileFILE sameFileDiffHost sameFileDiffPort sameFileDiffAlgorithm sameFileDiffAlgorithm2 sameFileDiffAlgorithm3 sameFileDiffAlgorithm4 sameFileDiffDigest sameFileDiffRef sameFileDiffQuery

#        sameFileTheSame
sameFileTheSame.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileTheSame.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789

#        sameFileAlgorithmCase
sameFileAlgorithmCase.httpmdURL=httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
sameFileAlgorithmCase.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789

#        sameFileDigestCase
sameFileDigestCase.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileDigestCase.anotherURL=httpmd://localhost/file.jar;MD5=ABCDEFabcdef0123456789

#        sameFileCommentsIgnore
sameFileCommentsIgnore.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileCommentsIgnore.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms

#        sameFileCommentsIgnore2
sameFileCommentsIgnore2.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms1
sameFileCommentsIgnore2.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789,comms2

#        sameFilePort
sameFilePort.httpmdURL=httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789
sameFilePort.anotherURL=httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789

#        sameFileDefaultPort
sameFileDefaultPort.httpmdURL=httpmd://localhost:80/file.jar;MD5=abcdefABCDEF0123456789
sameFileDefaultPort.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789

#        sameFileDefaultDiffPort
sameFileDefaultDiffPort.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileDefaultDiffPort.anotherURL=httpmd://localhost:8001/file.jar;MD5=abcdefABCDEF0123456789
sameFileDefaultDiffPort.expResult=false

#        sameFileRef
#sameFileRef.httpmdURL=httpmd://localhost/file.jar;MD5=abc#Ref
# Harness workaround
sameFileRef.httpmdURL=httpmd://localhost/file.jar;MD5=abc<PoundSign>Ref
#sameFileRef.anotherURL=httpmd://localhost/file.jar;MD5=abc#Ref
# Harness workaround
sameFileRef.anotherURL=httpmd://localhost/file.jar;MD5=abc<PoundSign>Ref

#        sameFileQuery
sameFileQuery.httpmdURL=httpmd://localhost/file.jar;MD5=abc?query
sameFileQuery.anotherURL=httpmd://localhost/file.jar;MD5=abc?query

#        sameFileHTTP
sameFileHTTP.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileHTTP.anotherURL=http://localhost/file.jar
sameFileHTTP.expResult=false

#        sameFileFILE
sameFileFILE.httpmdURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileFILE.anotherURL=file:$/file.jar
sameFileFILE.expResult=false

#        sameFileDiffHost
sameFileDiffHost.httpmdURL=httpmd://remotehost/file.jar;MD5=abcdefABCDEF0123456789
sameFileDiffHost.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileDiffHost.expResult=false

#        sameFileDiffPort
sameFileDiffPort.httpmdURL=httpmd://localhost:8000/file.jar;MD5=abcdefABCDEF0123456789
sameFileDiffPort.anotherURL=httpmd://localhost:8001/file.jar;MD5=abcdefABCDEF0123456789
sameFileDiffPort.expResult=false

#        sameFileDiffAlgorithm
sameFileDiffAlgorithm.httpmdURL=httpmd://localhost/file.jar;SHA=abcdefABCDEF0123456789
sameFileDiffAlgorithm.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileDiffAlgorithm.expResult=false

#        sameFileDiffAlgorithm2
sameFileDiffAlgorithm2.httpmdURL=httpmd://localhost/file.jar;SHA=abcdefABCDEF0123456789
sameFileDiffAlgorithm2.anotherURL=httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
sameFileDiffAlgorithm2.expResult=false

#        sameFileDiffAlgorithm3
sameFileDiffAlgorithm3.httpmdURL=httpmd://localhost/file.jar;sha=abcdefABCDEF0123456789
sameFileDiffAlgorithm3.anotherURL=httpmd://localhost/file.jar;md5=abcdefABCDEF0123456789
sameFileDiffAlgorithm3.expResult=false

#        sameFileDiffAlgorithm4
sameFileDiffAlgorithm4.httpmdURL=httpmd://localhost/file.jar;sha=abcdefABCDEF0123456789
sameFileDiffAlgorithm4.anotherURL=httpmd://localhost/file.jar;MD5=abcdefABCDEF0123456789
sameFileDiffAlgorithm4.expResult=false

#        sameFileDiffDigest
sameFileDiffDigest.httpmdURL=httpmd://localhost/file.jar;MD5=abcdef
sameFileDiffDigest.anotherURL=httpmd://localhost/file.jar;MD5=0123456789
sameFileDiffDigest.expResult=false

#        sameFileDiffRef
#sameFileDiffRef.httpmdURL=httpmd://localhost/file.jar;MD5=abc#Ref1
# Harness workaround
sameFileDiffRef.httpmdURL=httpmd://localhost/file.jar;MD5=abc<PoundSign>Ref1
#sameFileDiffRef.anotherURL=httpmd://localhost/file.jar;MD5=abc#Ref2
# Harness workaround
sameFileDiffRef.anotherURL=httpmd://localhost/file.jar;MD5=abc<PoundSign>Ref2

#        sameFileDiffQuery
sameFileDiffQuery.httpmdURL=httpmd://localhost/file.jar;MD5=abc?query1
sameFileDiffQuery.anotherURL=httpmd://localhost/file.jar;MD5=abc?query2
sameFileDiffQuery.expResult=false
include0=../../url.properties
