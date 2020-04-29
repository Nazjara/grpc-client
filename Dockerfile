FROM alpine:latest
WORKDIR /opt/grpc/
COPY target/grpc-client-*.jar .
RUN apk add --no-cache openjdk11  && \
    mv grpc-client-*.jar grpc-client.jar
ENTRYPOINT ["/usr/bin/java"]
CMD ["-jar", "/opt/grpc/grpc-client.jar"]
VOLUME /var/lib/grpc/config-repo
ENV SERVER_HOST=grpc-server
ENV SERVER_PORT=8080
EXPOSE 8080