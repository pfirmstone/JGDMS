Documentation Documentation
===========================

Doc Doc - Goose?
----------------

Here is some documentation about how this documentation is written. Feel free to pitch-in and improve things.

  1. Markdown - Some of this site is written in the [Markdown format](http://daringfireball.net/projects/markdown/syntax).
   Markdown source files are located in: [src/site/markdown](https://github.com/bhamail/pinexus/tree/master/src/site/markdown).
    
  2. You may also find a `README.md` file at the project root as well.


Doc Publishing
--------------

To deploy these docs to GH Pages, we use [maven-scm-publish-plugin](https://maven.apache.org/plugins/maven-scm-publish-plugin/).

Due to issues publishing a large multi-module site, we need to run a number of commands in order to publish the site:

  1. Ensure a truly fresh copy of the site files.

         mvn clean

  2. Generate the entire site (including all sub-modules) in the `target/staging` folder.

         mvn clean package site site:stage

  3. Publish the new site to github gh-pages branch.

         mvn scm-publish:publish-scm

     Shorter commands (like `mvn clean site-deploy`) run into a number of problems. If you find
     a shorter incantation that works reliably, please share your findings! 

