
package com.project.resource_reservation_engine.scripts;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrencyTest {

    static final String BASE_URL = "http://localhost:8080";

    static final String ADMIN_TOKEN = "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbkByZXNlcnZhdGlvbi1lbmdpbmUubG9jYWwiLCJyb2xlIjoiQURNSU4iLCJpYXQiOjE3ODU0Nzc1NzYsImV4cCI6MTc4NTU2Mzk3Nn0.XUWX9DvTDxwZtD4B_9k-5zgtBbeLtXIOBWK5WZu9CE-f7ABPjdxL-f98bvX_bmIo";

    static final List<String> USER_TOKENS = List.of(
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjFAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTQ3NzYwOCwiZXhwIjoxNzg1NTY0MDA4fQ.O362m2a2XMs-t4KUyN4ekFBCCVlehdIdNY9Kn2P4K9TgUB8eW0LpE4o141JhKYam",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjJAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTQ3NzYyNywiZXhwIjoxNzg1NTY0MDI3fQ._KefuqTxf0JnTuQgycdObwH8ZaL1GvBpZ1Qh1SOEgV8lpbTVPiy-6xTRyucbWHIF",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjNAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTQ3NzY0MiwiZXhwIjoxNzg1NTY0MDQyfQ.UuL1Iv2sXug-Cyb6oMvnsFBYokxWJQl3v6yTuqQ8DTpUBt6I6tB0vqyMGTsbxB4l",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjRAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTQ3NzY1OCwiZXhwIjoxNzg1NTY0MDU4fQ.z6b9o-5jsACvrH-niO2fb-30c7E-7YYaa2JVJmWHRpsw23TNXE-uRzXvldcxfiv1",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjVAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTQ3NzY3MiwiZXhwIjoxNzg1NTY0MDcyfQ.2R2zCCtPFBYE5MBsal_C9mZXiHmdD0P0dwAw1Xe5QMntN2rJEm-ZEZm-dJTBhPIN"
    );

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Create a capacity-2 resource as admin
        String createBody = """
            {"name":"Concurrency Test Room","capacity":2}
            """;
        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/resources"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(createBody))
                .build();
        HttpResponse<String> createResp = client.send(createReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("Resource creation: " + createResp.statusCode() + " " + createResp.body());

        // crude extraction of "id":<number> from the response — fine for a throwaway test script
        String body = createResp.body();
        String idStr = body.replaceAll(".*\"id\":(\\d+).*", "$1");
        long resourceId = Long.parseLong(idStr);
        System.out.println("Using resourceId = " + resourceId);
        System.out.println();

        // 2. Fire all 5 user bookings simultaneously via CountDownLatch
        int n = USER_TOKENS.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch readyLatch = new CountDownLatch(n);   // all threads report ready
        CountDownLatch startLatch = new CountDownLatch(1);   // main releases all at once
        CountDownLatch doneLatch = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            String token = USER_TOKENS.get(i);
            int userIndex = i + 1;
            pool.submit(() -> {
                try {
                    String bookingBody = "{\"resourceId\":" + resourceId + "}";
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/bookings"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .POST(HttpRequest.BodyPublishers.ofString(bookingBody))
                            .build();

                    readyLatch.countDown();
                    startLatch.await(); // block until released simultaneously

                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    System.out.println("User " + userIndex + " -> " + resp.statusCode() + " " + resp.body());
                } catch (Exception e) {
                    System.out.println("User " + userIndex + " -> ERROR " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();   // wait for all threads to be built and waiting
        startLatch.countDown(); // release all at once
        doneLatch.await();

        pool.shutdown();
        System.out.println();
        System.out.println("Done. Expect: 2x 201 CONFIRMED, rest split between 409 SLOT_FULL and 409 VERSION_CONFLICT.");
    }
}