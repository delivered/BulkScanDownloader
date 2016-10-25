[![Build Status](https://travis-ci.org/delivered/BulkScanDownloader.svg?branch=master)](https://travis-ci.org/delivered/BulkScanDownloader)

# Command-line BulkScanDownloader

Earth Class Mail provides a windows desktop application for downloading all of your mail scans in bulk, called BulkScanDownloader. This application is great for those with a few users and want to do one-time or semi-regular downloads/local backups up all your mail scans.

The application is however not very well suited to be run with hundreds of users in a server environment where downloads are constantly happening.

This command-line client is a Java application built to be easily deployable with standard Java tools to any server-side environment. It is easily configurable with a JSON configuration file and will download all scans to a configureable location on the system, optionally with a subfolder for every user.

## Disclaimer

This application uses v1 of the Earth Class Mail API which will soon be deprecated in favor of a much improved v2. If you’re interested in integrating with our service, get in touch and we can provide a more detailed roadmap.

## Building

If you do not wish to build the project yourself, you should be able to download the JAR file of the latest version build from [the Github Releases page](https://github.com/delivered/BulkScanDownloader/releases).

This appplication is a standard no-frills Java (maven) application designed to be as portable as possible. The application uses the `java.nio` package to do some file path operations, this package was introduced in jdk7 so the application requires jdk7 or later. If need be, it should be easy to remove this dependency.

The [`pom.xml`](https://github.com/delivered/BulkScanDownloader/blob/master/pom.xml) file in the root directory declares the build-process and dependencies and should be able to cleanly compile and package using [Maven](https://maven.apache.org/).

Running

	mvn package

should yield something like the following output

```
[INFO] Scanning for projects...
[INFO]
[INFO] Using the builder org.apache.maven.lifecycle.internal.builder.singlethreaded.SingleThreadedBuilder with a thread count of 1
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building bulkscandownloader 0.0.1
[INFO] ------------------------------------------------------------------------
[...]
[INFO] com/sun/ already added, skipping
[INFO] javax/ already added, skipping
[INFO] Building jar: /your/code/dir/bulkscandownloader/target/bulkscandownloader-0.0.1-jar-with-dependencies.jar
[INFO] META-INF/ already added, skipping
[INFO] META-INF/MANIFEST.MF already added, skipping
[...]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 7.044 s
[...]
```

Now you will have a JAR packages with all dependencies at `target/bulkscandownloader-0.0.1-jar-with-dependencies.jar`.

### Dependencies

Dependecy             | Version | Use
----------------------|---------|-----
Axis                  | 1.4     | Used by the auto-generated SOAP API client
jaxrpc-api            | 1.1     | Used to process the SOAP requests
jersey-client         | 2.8     | The web client used for the JSON API
jackson-mapper-asl    | 1.9.13  | Used to map JSON to Java classes
commons-io            | 2.4     | Provides the IOUtils class used for stream processing
log4j-api             | 2.3     | Log4j is used for logging
log4j-core            | 2.3     | Log4j is used for logging
mail                  | 1.4.7   | Added to suppress warning from axis that attachments are not supported without it, but attachments are not used


## Configuration

Application configuration is read from the `bulkscandownloader.json` file that should be located in the same directory as the application is run from (current working directory at time of application execution).

A sample configuration file can be found in the root directory of this repository. Here is what the configuration file should look like and an explanation of the provided configuration options:

```
{
  // The directory which all downloads should be put into,
  // can be absolute or relative
  "rootDir": "~/bulkscan",

  // The post download action wanted for mail pieces
  // 0 means no action is taken
  // 1 means to recycle the item
  // 2 means to shred the item
  "postDownloadAction": 0,

  // Whether or not to use subfolders for the file downloads
  // if true, a subfolder structure like `rootDir/username/mailbox-name/file.jpg`
  // will be used
  // if false, a structure like `rootDir/username/file.jpg` will be used
  "useFolders": true,

  // The list of users that the application should download mail for
  "users": [
    {
    	"active": true,           // Don't download mail for this user if false
    	"username": "johndoe",    // Username
    	"password": "johndoex",   // Password
    	"envelopeImages": true,   // If envelope images should be downloaded
    	"allEnvelopeSides": true, // If all (not just first) envelope sides should be downloaded
    	"contentScans": true      // If content scans should be downloaded
    }
  ]
}
```

## Running

Once you have your JAR file and your configuration file, running the program should be as easy as running

	java -jar bulkscandownloader-0.0.1-jar-with-dependencies.jar

from the same directory as your configuration file is.

### Debugging / Testing

If you wish to test this application, you should turn the [`DEBUG_BUILD`](https://github.com/delivered/BulkScanDownloader/blob/master/src/main/java/bulkscandownloader/BulkScanDownloader.java#L21) variable to `true` in `BulkScanDownloader.java`. When this variable is true the mail downloads are not sent acknowledgement of download and so won't be flagged as downloaded, the result being that you will keep downloading the same mail over and over again, something that might be desirable for testing.

## A mention on the API

I won't dig into the exact details of the API as that is more readily available in the code anyway. However, the Java client for the Content Download API in this project has been automatically generated from the accompanying `ContentDownloadService.wsdl` file.

## License

This software is provided AS IS under the [BSD 3-Clause License](http://opensource.org/licenses/BSD-3-Clause).

```
Copyright (c) 2015, Delivered.io, Inc
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Delivered.io, Inc nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL DELIVERED.IO, INC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
