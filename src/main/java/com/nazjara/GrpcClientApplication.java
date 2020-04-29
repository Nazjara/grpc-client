package com.nazjara;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@RestController
@Slf4j
public class GrpcClientApplication {

    @Value("${SERVER_HOST}")
    private String host;

    @Value("${SERVER_PORT}")
    private int port;

    public static void main(String[] args) {
        SpringApplication.run(GrpcClientApplication.class, args);
    }

    @GetMapping(path = "/process", produces = MediaType.TEXT_PLAIN_VALUE)
    public String processRequest() throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        ExampleServiceGrpc.ExampleServiceStub asyncStub = ExampleServiceGrpc.newStub(channel);
        ExampleServiceGrpc.ExampleServiceBlockingStub blockingStub = ExampleServiceGrpc.newBlockingStub(channel);

        log.info("callSingleRpc starting");
        callSingleRpc(blockingStub);
        log.info("callSingleRpc finished");

        Thread.sleep(5000);

        log.info("callServerSideStreamingRpc starting");
        callServerSideStreamingRpc(blockingStub);
        log.info("callServerSideStreamingRpc finished");

        Thread.sleep(5000);

        log.info("callClientSideStreamingRpc starting");
        callClientSideStreamingRpc(asyncStub);
        log.info("callClientSideStreamingRpc finished");

        Thread.sleep(5000);

        log.info("callBidirectionalStreamingRpc starting");
        callBidirectionalStreamingRpc(asyncStub);
        log.info("callBidirectionalStreamingRpc finished");

        return "Request processed";
    }

    private void callSingleRpc(ExampleServiceGrpc.ExampleServiceBlockingStub blockingStub) {
        log.info("singleRpc returned response: " + blockingStub.singleRpc(Request.newBuilder().setValue("value").build()).getValue());
    }

    private void callServerSideStreamingRpc(ExampleServiceGrpc.ExampleServiceBlockingStub blockingStub) {
        blockingStub.serverSideStreamingRpc(Request.newBuilder().setValue("value").build())
                .forEachRemaining(response1 -> {
                    log.info("serverSideStreamingRpc returned response: " + response1.getValue());
                });
    }

    private void callClientSideStreamingRpc(ExampleServiceGrpc.ExampleServiceStub asyncStub) throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<Response> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(Response response) {
                log.info("Response received for callClientSideStreamingRpc request: " + response.getValue());
            }

            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                log.error("callClientSideStreamingRpc failed: " + status);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                log.info("Finished callClientSideStreamingRpc");
                finishLatch.countDown();
            }
        };

        StreamObserver<Request> requestObserver = asyncStub.clientSideStreamingRpc(responseObserver);

        for (String s : new String[]{"e", "u", "l", "a", "v"}) {
            log.info("Sending request on callClientSideStreamingRpc: " + s);
            requestObserver.onNext(Request.newBuilder().setValue(s).build());
            Thread.sleep(1000);

            if (finishLatch.getCount() == 0) {
                // RPC completed or errored before we finished sending.
                // Sending further requests won't error, but they will just be thrown away.
                return;
            }
        }

        requestObserver.onCompleted();
        finishLatch.await(1, TimeUnit.MINUTES);
    }

    private void callBidirectionalStreamingRpc(ExampleServiceGrpc.ExampleServiceStub asyncStub) throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<Response> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(Response response) {
                log.info("Response received for callBidirectionalStreamingRpc request: " + response.getValue());
            }

            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                log.error("callBidirectionalStreamingRpc failed: " + status);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                log.info("Finished callBidirectionalStreamingRpc");
                finishLatch.countDown();
            }
        };

        StreamObserver<Request> requestObserver = asyncStub.bidirectionalStreamingRpc(responseObserver);

        for (String s : new String[]{"value1", "value2", "value3", "value4", "value5"}) {
            log.info("Sending request on callBidirectionalStreamingRpc: " + s);
            requestObserver.onNext(Request.newBuilder().setValue(s).build());
            Thread.sleep(1000);

            if (finishLatch.getCount() == 0) {
                // RPC completed or errored before we finished sending.
                // Sending further requests won't error, but they will just be thrown away.
                return;
            }
        }

        requestObserver.onCompleted();
        finishLatch.await(1, TimeUnit.MINUTES);
    }
}