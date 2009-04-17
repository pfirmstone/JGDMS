testClass=HashCode
testCategories=url_spec,url

# HashCode Test Case names
HashCode.testCases=hashCodeSameURL hashCodeComments hashCodeCapitalLetters hashCodeAlgorithm

#        hashCodeSameURL
hashCodeSameURL.Url1=httpmd://localhost/file.jar;md5=aaaaaaa
hashCodeSameURL.Url2=${hashCodeSameURL.Url1}

#        hashCodeComments
hashCodeComments.Url1=httpmd://localhost/file.jar;md5=aaaaaaa
hashCodeComments.Url2=httpmd://localhost/file.jar;md5=aaaaaaa,comments

#        hashCodeCapitalLetters
hashCodeCapitalLetters.Url1=httpmd://localhost/file.jar;md5=AAAAAAA
hashCodeCapitalLetters.Url2=httpmd://localhost/file.jar;md5=aaaaaaa

#        hashCodeAlgorithm
hashCodeAlgorithm.Url1=httpmd://localhost/file.jar;MD5=aaaaaaa
hashCodeAlgorithm.Url2=httpmd://localhost/file.jar;md5=aaaaaaa
include0=../../url.properties
