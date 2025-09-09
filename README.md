# Burp Suite Extension

This is a Burp Suite extension that handles HTTP requests and send it to a NATS server.

# Build

```bash
gradle clean build
```

The compiled extension JAR file will be generated in the `build/libs/` directory.

# Run

* Update the `mats.properties` file and set the `matsUrl` with the URL of the MATS server
* Copy the `mats.propertise` file to your home directory
* Add the extension to Burp by adding the jar built manually. In headless mode, add the following entry to the configuration file under the `extender` property:

```
"extensions":[
                {
                    "errors":"ui",
                    "extension_file":"/home/remy/Burp-1.0.0.jar",
                    "extension_type":"java",
                    "loaded":true,
                    "name":"Burp HTTP Handler",
                    "output":"ui",
                    "use_ai":false
                }
            ]
```



