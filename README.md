# Kakka

For details, see the
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

## Liveness and readyiness
In production, kakka can be probed with:
- http://<host>:<port>/health/live
- http://<host>:<port>/health/ready
to check liveness and readiness, accordingly

* Kakka currently has two spring profiles, dev and test. Use the application.properties file to switch between these.
* The application is unable to run without configuration. This must be defined externally to the application in a file called application.properties. Copy application.properties into either the current directory, i.e. where the application will be run from, or a /config subdirectory of this folder
* Typical application.properties for dev environment:

```
shutdown.timeout=1

blobstore.gcs.container.name=marduk-test
blobstore.gcs.credential.path=/home/tomgag/.ssh/Carbon-ef49cabc6d04.json
blobstore.delete.external.blobs=false
blobstore.gcs.project.id=carbon-1287

camel.springboot.name=Kakka

logging.config=classpath:logback.xml
logging.level.no=DEBUG
logging.level.no.entur.kakka=INFO
logging.level.org=INFO
logging.level.org.apache.camel.util=INFO


logging.level.org.apache=INFO
logging.level.org.apache=INFO
logging.level.org.apache.http.wire=INFO
logging.level.org.apache.activemq=INFO
logging.level.org.apache.activemq.thread=INFO
logging.level.org.apache.activemq.transport=INFO
logging.level.org.apache.camel.component.file=INFO
logging.level.org.apache.camel.component.file.remote=INFO
logging.level.org.springframework=INFO

server.admin.host=0.0.0.0
server.admin.port=8888
server.host=0.0.0.0
server.port=8776

spring.activemq.broker-url=tcp://localhost:51616?jms.redeliveryPolicy.maximumRedeliveries=0
activemq.broker.host=localhost
activemq.broker.mgmt.port=18161
spring.activemq.password=admin
spring.activemq.user=admin
spring.activemq.pooled=true
spring.main.sources=no.entur.kakka
spring.profiles.active=gcs-blobstore

tiamat.url=http4://tiamat:1888
babylon.url=http4://babylon:9030/rest

kartverket.username=
kartverket.password=

```
* Run with maven `mvn spring-boot:run -Dspring.profiles.active=dev`

* Build: `mvn clean install`
* Local run: `java -Xmx1280m -Dspring.profiles.active=dev -jar target/kakka-0.0.1-SNAPSHOT.jar`
* Docker image: `mvn -Dspring.profiles.active=dev -Pf8-build`
* Run the docker image in docker inside vagrant:

     ```docker rm -f kakka ; mvn -Pf8-build && docker run -it --name kakka -e JAVA_OPTIONS="-Xmx1280m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" --link activemq --link lamassu  -v /git/config/kakka/dev/application.properties:/app/config/application.properties:ro dr.rutebanken.org/rutebanken/kakka:0.0.1-SNAPSHOT```

* For more docker plugin goals, see: http://ro14nd.de/docker-maven-plugin/goals.html

