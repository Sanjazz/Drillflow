# WitsmlApi-Server
A dockerized WITSML API Server that is agnostic of the backend.

# Notes 

At the moment this project only runs in Java 8...it needs to be upgraded to run in 11

# Building

Execute `mvn clean install`

# Running

Execute `java -jar target/server-0.0.1-SNAPSHOT.jar`

# Building the Docker image

Navigate to the docker directory

Execute `docker build . -t hashmapinc/witsmlserver:latest` to build the image

Once completed execute `docker run -p 7070:7070 hashmapinc/witsmlserver:latest` 