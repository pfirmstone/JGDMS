testClass=ComputeDigest
testCategories=url_spec,url

# ComputeDigest Test Case names
ComputeDigest.testCases=computeDigestNonEmptySHA-256file computeDigestNonEmptySHA-256http computeDigestNonEmptySHA-256httpmd computeDigestNonEmptySHA-384file computeDigestNonEmptySHA-384http computeDigestNonEmptySHA-384httpmd computeDigestJarSHA-256file computeDigestJarSHA-256http computeDigestJarSHA-256httpmd computeDigestJarSHA-384file computeDigestJarSHA-384http computeDigestJarSHA-384httpmd computeDigestEmptySHA-256file computeDigestEmptySHA-256http computeDigestEmptySHA-256httpmd computeDigestEmptySHA-384file computeDigestEmptySHA-384http computeDigestEmptySHA-384httpmd computeDigestNonExistSHA-256file computeDigestNonExistSHA-256http computeDigestNonExistSHA-256httpmd computeDigestBadUrl computeDigestBadAlgorithmInUrl computeDigestBadAlgorithmfile computeDigestBadAlgorithmhttp computeDigestBadAlgorithmhttpmd computeDigestNullAlgorithm computeDigestNullUrl computeDigestNull

# The second HTTP Server parameters
ComputeDigest.SecondHTTPServer.impl=org.apache.river.tool.ClassServer
ComputeDigest.SecondHTTPServer.port=8083
ComputeDigest.SecondHTTPServer.dir=${java.io.tmpdir}
ComputeDigest.SecondHTTPServer.type=classServer

#        computeDigestNonEmptySHA-256file
computeDigestNonEmptySHA-256file.Protocol=file
computeDigestNonEmptySHA-256file.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHA-256file.FileName=nonempty_file
computeDigestNonEmptySHA-256file.Algorithm=SHA-256

#        computeDigestNonEmptySHA-256http
computeDigestNonEmptySHA-256http.Protocol=http
computeDigestNonEmptySHA-256http.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonEmptySHA-256http.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHA-256http.FileName=nonempty_file
computeDigestNonEmptySHA-256http.Algorithm=SHA-256

#        computeDigestNonEmptySHA-256httpmd
computeDigestNonEmptySHA-256httpmd.Protocol=httpmd
computeDigestNonEmptySHA-256httpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonEmptySHA-256httpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHA-256httpmd.FileName=nonempty_file;SHA-256=aaa,comments
computeDigestNonEmptySHA-256httpmd.Algorithm=SHA-256

#        computeDigestNonEmptySHA-384file
computeDigestNonEmptySHA-384file.Protocol=file
computeDigestNonEmptySHA-384file.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHA-384file.FileName=nonempty_file
computeDigestNonEmptySHA-384file.Algorithm=SHA-384

#        computeDigestNonEmptySHA-384http
computeDigestNonEmptySHA-384http.Protocol=http
computeDigestNonEmptySHA-384http.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonEmptySHA-384http.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHA-384http.FileName=nonempty_file
computeDigestNonEmptySHA-384http.Algorithm=SHA-384

#        computeDigestNonEmptySHA-384httpmd
computeDigestNonEmptySHA-384httpmd.Protocol=httpmd
computeDigestNonEmptySHA-384httpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonEmptySHA-384httpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHA-384httpmd.FileName=nonempty_file;SHA-384=aaa,comments
computeDigestNonEmptySHA-384httpmd.Algorithm=SHA-384

#        computeDigestJarSHA-256file
computeDigestJarSHA-256file.Protocol=file
computeDigestJarSHA-256file.SrcDir=${testClassServer.dir}
computeDigestJarSHA-256file.FileName=qa1-httpmd.jar
computeDigestJarSHA-256file.Algorithm=SHA-256

#        computeDigestJarSHA-256http
computeDigestJarSHA-256http.Protocol=http
computeDigestJarSHA-256http.Port=${testClassServer.port}
computeDigestJarSHA-256http.SrcDir=${testClassServer.dir}
computeDigestJarSHA-256http.FileName=qa1-httpmd.jar
computeDigestJarSHA-256http.Algorithm=SHA-256

#        computeDigestJarSHA-256httpmd
computeDigestJarSHA-256httpmd.Protocol=httpmd
computeDigestJarSHA-256httpmd.Port=${testClassServer.port}
computeDigestJarSHA-256httpmd.SrcDir=${testClassServer.dir}
computeDigestJarSHA-256httpmd.FileName=qa1-httpmd.jar;SHA-256=aaaaa,comments
computeDigestJarSHA-256httpmd.Algorithm=SHA-256

#        computeDigestJarSHA-384file
computeDigestJarSHA-384file.Protocol=file
computeDigestJarSHA-384file.SrcDir=${testClassServer.dir}
computeDigestJarSHA-384file.FileName=qa1-httpmd.jar
computeDigestJarSHA-384file.Algorithm=SHA-384

#        computeDigestJarSHA-384http
computeDigestJarSHA-384http.Protocol=http
computeDigestJarSHA-384http.Port=${testClassServer.port}
computeDigestJarSHA-384http.SrcDir=${testClassServer.dir}
computeDigestJarSHA-384http.FileName=qa1-httpmd.jar
computeDigestJarSHA-384http.Algorithm=SHA-384

#        computeDigestJarSHA-384httpmd
computeDigestJarSHA-384httpmd.Protocol=httpmd
computeDigestJarSHA-384httpmd.Port=${testClassServer.port}
computeDigestJarSHA-384httpmd.SrcDir=${testClassServer.dir}
computeDigestJarSHA-384httpmd.FileName=qa1-httpmd.jar;SHA-384=aaaaa,comments
computeDigestJarSHA-384httpmd.Algorithm=SHA-384

#        computeDigestEmptySHA-256file
computeDigestEmptySHA-256file.Protocol=file
computeDigestEmptySHA-256file.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHA-256file.FileName=empty_file
computeDigestEmptySHA-256file.Algorithm=SHA-256

#        computeDigestEmptySHA-256http
computeDigestEmptySHA-256http.Protocol=http
computeDigestEmptySHA-256http.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestEmptySHA-256http.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHA-256http.FileName=empty_file
computeDigestEmptySHA-256http.Algorithm=SHA-256

#        computeDigestEmptySHA-256httpmd
computeDigestEmptySHA-256httpmd.Protocol=httpmd
computeDigestEmptySHA-256httpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestEmptySHA-256httpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHA-256httpmd.FileName=empty_file;SHA-256=aaa,comments
computeDigestEmptySHA-256httpmd.Algorithm=SHA-256

#        computeDigestEmptySHA-384file
computeDigestEmptySHA-384file.Protocol=file
computeDigestEmptySHA-384file.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHA-384file.FileName=empty_file
computeDigestEmptySHA-384file.Algorithm=SHA-384

#        computeDigestEmptySHA-384http
computeDigestEmptySHA-384http.Protocol=http
computeDigestEmptySHA-384http.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestEmptySHA-384http.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHA-384http.FileName=empty_file
computeDigestEmptySHA-384http.Algorithm=SHA-384

#        computeDigestEmptySHA-384httpmd
computeDigestEmptySHA-384httpmd.Protocol=httpmd
computeDigestEmptySHA-384httpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestEmptySHA-384httpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHA-384httpmd.FileName=empty_file;SHA-384=aaa,comments
computeDigestEmptySHA-384httpmd.Algorithm=SHA-384

#        computeDigestNonExistSHA-256file
computeDigestNonExistSHA-256file.Protocol=file
computeDigestNonExistSHA-256file.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonExistSHA-256file.FileName=nonexist_file
computeDigestNonExistSHA-256file.Algorithm=SHA-256
computeDigestNonExistSHA-256file.Expected=java.io.IOException

#        computeDigestNonExistSHA-256http
computeDigestNonExistSHA-256http.Protocol=http
computeDigestNonExistSHA-256http.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonExistSHA-256http.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonExistSHA-256http.FileName=nonexist_file
computeDigestNonExistSHA-256http.Algorithm=SHA-256
computeDigestNonExistSHA-256http.Expected=java.io.IOException

#        computeDigestNonExistSHA-256httpmd
computeDigestNonExistSHA-256httpmd.Protocol=httpmd
computeDigestNonExistSHA-256httpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonExistSHA-256httpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonExistSHA-256httpmd.FileName=nonexist_file;SHA-256=aaa,comments
computeDigestNonExistSHA-256httpmd.Algorithm=SHA-256
computeDigestNonExistSHA-256httpmd.Expected=java.io.IOException

#        computeDigestBadUrl
computeDigestBadUrl.Protocol=httpmd
computeDigestBadUrl.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestBadUrl.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestBadUrl.FileName=nonempty_file;md5=,comments
computeDigestBadUrl.Algorithm=SHA-256
computeDigestBadUrl.Expected=java.io.IOException

#        computeDigestBadAlgorithmInUrl
computeDigestBadAlgorithmInUrl.Protocol=httpmd
computeDigestBadAlgorithmInUrl.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestBadAlgorithmInUrl.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestBadAlgorithmInUrl.FileName=nonempty_file;BADALG=,comments
computeDigestBadAlgorithmInUrl.Algorithm=SHA-256
computeDigestBadAlgorithmInUrl.Expected=java.io.IOException

#        computeDigestBadAlgorithmfile
computeDigestBadAlgorithmfile.Protocol=file
computeDigestBadAlgorithmfile.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestBadAlgorithmfile.FileName=nonempty_file
computeDigestBadAlgorithmfile.Algorithm=BADALG
computeDigestBadAlgorithmfile.Expected=java.security.NoSuchAlgorithmException

#        computeDigestBadAlgorithmhttp
computeDigestBadAlgorithmhttp.Protocol=http
computeDigestBadAlgorithmhttp.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestBadAlgorithmhttp.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestBadAlgorithmhttp.FileName=nonempty_file
computeDigestBadAlgorithmhttp.Algorithm=BADALG
computeDigestBadAlgorithmhttp.Expected=java.security.NoSuchAlgorithmException

#        computeDigestBadAlgorithmhttpmd
computeDigestBadAlgorithmhttpmd.Protocol=httpmd
computeDigestBadAlgorithmhttpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestBadAlgorithmhttpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestBadAlgorithmhttpmd.FileName=nonempty_file;SHA-384=aaa,comments
computeDigestBadAlgorithmhttpmd.Algorithm=BADALG
computeDigestBadAlgorithmhttpmd.Expected=java.security.NoSuchAlgorithmException

#        computeDigestNullAlgorithm
computeDigestNullAlgorithm.Protocol=httpmd
computeDigestNullAlgorithm.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNullAlgorithm.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNullAlgorithm.FileName=nonempty_file;SHA-256=aaa,comments
computeDigestNullAlgorithm.Expected=java.lang.NullPointerException

#        computeDigestNullUrl
computeDigestNullUrl.Protocol=httpmd
computeDigestNullUrl.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNullUrl.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNullUrl.Expected=java.lang.NullPointerException
computeDigestNullUrl.Algorithm=SHA-256

#        computeDigestNull
computeDigestNull.Protocol=httpmd
computeDigestNull.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNull.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNull.Expected=java.lang.NullPointerException
include0=../../url.properties
