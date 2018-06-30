FROM java:8-alpine
MAINTAINER Adam Niederer <adam.niederer@gmail.com>

ADD target/stor-api-0.0.1-SNAPSHOT-standalone.jar /stor-api/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/stor-api/app.jar"]
