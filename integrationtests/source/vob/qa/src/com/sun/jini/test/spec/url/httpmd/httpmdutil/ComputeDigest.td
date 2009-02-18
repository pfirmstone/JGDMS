testClass=ComputeDigest
testCategories=url_spec,url

# ComputeDigest Test Case names
ComputeDigest.testCases=computeDigestNonEmptyMD5file computeDigestNonEmptyMD5http computeDigestNonEmptyMD5httpmd computeDigestNonEmptySHAfile computeDigestNonEmptySHAhttp computeDigestNonEmptySHAhttpmd computeDigestJarMD5file computeDigestJarMD5http computeDigestJarMD5httpmd computeDigestJarSHAfile computeDigestJarSHAhttp computeDigestJarSHAhttpmd computeDigestEmptyMD5file computeDigestEmptyMD5http computeDigestEmptyMD5httpmd computeDigestEmptySHAfile computeDigestEmptySHAhttp computeDigestEmptySHAhttpmd computeDigestNonExistMD5file computeDigestNonExistMD5http computeDigestNonExistMD5httpmd computeDigestBadUrl computeDigestBadAlgorithmInUrl computeDigestBadAlgorithmfile computeDigestBadAlgorithmhttp computeDigestBadAlgorithmhttpmd computeDigestNullAlgorithm computeDigestNullUrl computeDigestNull

# The second HTTP Server parameters
ComputeDigest.SecondHTTPServer.impl=com.sun.jini.tool.ClassServer
ComputeDigest.SecondHTTPServer.port=8083
ComputeDigest.SecondHTTPServer.dir=${java.io.tmpdir}
ComputeDigest.SecondHTTPServer.type=classServer

#        computeDigestNonEmptyMD5file
computeDigestNonEmptyMD5file.Protocol=file
computeDigestNonEmptyMD5file.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptyMD5file.FileName=nonempty_file
computeDigestNonEmptyMD5file.Algorithm=MD5

#        computeDigestNonEmptyMD5http
computeDigestNonEmptyMD5http.Protocol=http
computeDigestNonEmptyMD5http.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonEmptyMD5http.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptyMD5http.FileName=nonempty_file
computeDigestNonEmptyMD5http.Algorithm=MD5

#        computeDigestNonEmptyMD5httpmd
computeDigestNonEmptyMD5httpmd.Protocol=httpmd
computeDigestNonEmptyMD5httpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonEmptyMD5httpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptyMD5httpmd.FileName=nonempty_file;MD5=aaa,comments
computeDigestNonEmptyMD5httpmd.Algorithm=MD5

#        computeDigestNonEmptySHAfile
computeDigestNonEmptySHAfile.Protocol=file
computeDigestNonEmptySHAfile.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHAfile.FileName=nonempty_file
computeDigestNonEmptySHAfile.Algorithm=SHA

#        computeDigestNonEmptySHAhttp
computeDigestNonEmptySHAhttp.Protocol=http
computeDigestNonEmptySHAhttp.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonEmptySHAhttp.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHAhttp.FileName=nonempty_file
computeDigestNonEmptySHAhttp.Algorithm=SHA

#        computeDigestNonEmptySHAhttpmd
computeDigestNonEmptySHAhttpmd.Protocol=httpmd
computeDigestNonEmptySHAhttpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonEmptySHAhttpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonEmptySHAhttpmd.FileName=nonempty_file;SHA=aaa,comments
computeDigestNonEmptySHAhttpmd.Algorithm=SHA

#        computeDigestJarMD5file
computeDigestJarMD5file.Protocol=file
computeDigestJarMD5file.SrcDir=${testClassServer.dir}
computeDigestJarMD5file.FileName=qa1-httpmd.jar
computeDigestJarMD5file.Algorithm=MD5

#        computeDigestJarMD5http
computeDigestJarMD5http.Protocol=http
computeDigestJarMD5http.Port=${testClassServer.port}
computeDigestJarMD5http.SrcDir=${testClassServer.dir}
computeDigestJarMD5http.FileName=qa1-httpmd.jar
computeDigestJarMD5http.Algorithm=MD5

#        computeDigestJarMD5httpmd
computeDigestJarMD5httpmd.Protocol=httpmd
computeDigestJarMD5httpmd.Port=${testClassServer.port}
computeDigestJarMD5httpmd.SrcDir=${testClassServer.dir}
computeDigestJarMD5httpmd.FileName=qa1-httpmd.jar;MD5=aaaaa,comments
computeDigestJarMD5httpmd.Algorithm=MD5

#        computeDigestJarSHAfile
computeDigestJarSHAfile.Protocol=file
computeDigestJarSHAfile.SrcDir=${testClassServer.dir}
computeDigestJarSHAfile.FileName=qa1-httpmd.jar
computeDigestJarSHAfile.Algorithm=SHA

#        computeDigestJarSHAhttp
computeDigestJarSHAhttp.Protocol=http
computeDigestJarSHAhttp.Port=${testClassServer.port}
computeDigestJarSHAhttp.SrcDir=${testClassServer.dir}
computeDigestJarSHAhttp.FileName=qa1-httpmd.jar
computeDigestJarSHAhttp.Algorithm=SHA

#        computeDigestJarSHAhttpmd
computeDigestJarSHAhttpmd.Protocol=httpmd
computeDigestJarSHAhttpmd.Port=${testClassServer.port}
computeDigestJarSHAhttpmd.SrcDir=${testClassServer.dir}
computeDigestJarSHAhttpmd.FileName=qa1-httpmd.jar;SHA=aaaaa,comments
computeDigestJarSHAhttpmd.Algorithm=SHA

#        computeDigestEmptyMD5file
computeDigestEmptyMD5file.Protocol=file
computeDigestEmptyMD5file.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptyMD5file.FileName=empty_file
computeDigestEmptyMD5file.Algorithm=MD5

#        computeDigestEmptyMD5http
computeDigestEmptyMD5http.Protocol=http
computeDigestEmptyMD5http.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestEmptyMD5http.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptyMD5http.FileName=empty_file
computeDigestEmptyMD5http.Algorithm=MD5

#        computeDigestEmptyMD5httpmd
computeDigestEmptyMD5httpmd.Protocol=httpmd
computeDigestEmptyMD5httpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestEmptyMD5httpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptyMD5httpmd.FileName=empty_file;MD5=aaa,comments
computeDigestEmptyMD5httpmd.Algorithm=MD5

#        computeDigestEmptySHAfile
computeDigestEmptySHAfile.Protocol=file
computeDigestEmptySHAfile.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHAfile.FileName=empty_file
computeDigestEmptySHAfile.Algorithm=SHA

#        computeDigestEmptySHAhttp
computeDigestEmptySHAhttp.Protocol=http
computeDigestEmptySHAhttp.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestEmptySHAhttp.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHAhttp.FileName=empty_file
computeDigestEmptySHAhttp.Algorithm=SHA

#        computeDigestEmptySHAhttpmd
computeDigestEmptySHAhttpmd.Protocol=httpmd
computeDigestEmptySHAhttpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestEmptySHAhttpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestEmptySHAhttpmd.FileName=empty_file;SHA=aaa,comments
computeDigestEmptySHAhttpmd.Algorithm=SHA

#        computeDigestNonExistMD5file
computeDigestNonExistMD5file.Protocol=file
computeDigestNonExistMD5file.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonExistMD5file.FileName=nonexist_file
computeDigestNonExistMD5file.Algorithm=MD5
computeDigestNonExistMD5file.Expected=java.io.IOException

#        computeDigestNonExistMD5http
computeDigestNonExistMD5http.Protocol=http
computeDigestNonExistMD5http.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonExistMD5http.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonExistMD5http.FileName=nonexist_file
computeDigestNonExistMD5http.Algorithm=MD5
computeDigestNonExistMD5http.Expected=java.io.IOException

#        computeDigestNonExistMD5httpmd
computeDigestNonExistMD5httpmd.Protocol=httpmd
computeDigestNonExistMD5httpmd.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNonExistMD5httpmd.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNonExistMD5httpmd.FileName=nonexist_file;MD5=aaa,comments
computeDigestNonExistMD5httpmd.Algorithm=MD5
computeDigestNonExistMD5httpmd.Expected=java.io.IOException

#        computeDigestBadUrl
computeDigestBadUrl.Protocol=httpmd
computeDigestBadUrl.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestBadUrl.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestBadUrl.FileName=nonempty_file;md5=,comments
computeDigestBadUrl.Algorithm=MD5
computeDigestBadUrl.Expected=java.io.IOException

#        computeDigestBadAlgorithmInUrl
computeDigestBadAlgorithmInUrl.Protocol=httpmd
computeDigestBadAlgorithmInUrl.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestBadAlgorithmInUrl.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestBadAlgorithmInUrl.FileName=nonempty_file;BADALG=,comments
computeDigestBadAlgorithmInUrl.Algorithm=MD5
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
computeDigestBadAlgorithmhttpmd.FileName=nonempty_file;SHA=aaa,comments
computeDigestBadAlgorithmhttpmd.Algorithm=BADALG
computeDigestBadAlgorithmhttpmd.Expected=java.security.NoSuchAlgorithmException

#        computeDigestNullAlgorithm
computeDigestNullAlgorithm.Protocol=httpmd
computeDigestNullAlgorithm.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNullAlgorithm.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNullAlgorithm.FileName=nonempty_file;MD5=aaa,comments
computeDigestNullAlgorithm.Expected=java.lang.NullPointerException

#        computeDigestNullUrl
computeDigestNullUrl.Protocol=httpmd
computeDigestNullUrl.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNullUrl.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNullUrl.Expected=java.lang.NullPointerException
computeDigestNullUrl.Algorithm=MD5

#        computeDigestNull
computeDigestNull.Protocol=httpmd
computeDigestNull.Port=${ComputeDigest.SecondHTTPServer.port}
computeDigestNull.SrcDir=${ComputeDigest.SecondHTTPServer.dir}
computeDigestNull.Expected=java.lang.NullPointerException
include0=../../url.properties
