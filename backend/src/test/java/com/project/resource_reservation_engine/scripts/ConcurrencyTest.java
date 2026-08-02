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

    static final String ADMIN_TOKEN = "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbkByZXNlcnZhdGlvbi1lbmdpbmUubG9jYWwiLCJyb2xlIjoiQURNSU4iLCJpYXQiOjE3ODU2NjIwMjUsImV4cCI6MTc4NTc0ODQyNX0.Pgw0BfcHIdPp2qMH-hAEHS2YNS2o3tyGi6DIZ3wbMOPGLHONnmRzNGr1hyAapDnH";

    static final List<String> USER_TOKENS = List.of(
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjFAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTY2MjAyOSwiZXhwIjoxNzg1NzQ4NDI5fQ.1xd3DUAmUM3zCtexs5yCh5pkMdPGdz_28Avk2JuhyFt8knIIlsRE97iKe-Qx2uT3",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjJAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTY2MjM4MiwiZXhwIjoxNzg1NzQ4NzgyfQ.-en34pDE-l-SlDSJFWzD7DaCQW-3voNm_8WXrQav_b2Kuh9f-VUC44IEDcO_Nchl",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjNAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTY2MjI4MywiZXhwIjoxNzg1NzQ4NjgzfQ.5XDNNytAX-Kriyn8daFcnYh5Z7pKLaAQsY5FI2DkeFrYV5wYrZnTRWRz5GcCs2fW",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjRAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTY2MjA0MywiZXhwIjoxNzg1NzQ4NDQzfQ.fX2-v2DtUgVWRnnlnxzWQcaTwyq3GUzBNsQa526OfV38RK68yrN_26j5AqZ2r3kI",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjVAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTY2MjA0NywiZXhwIjoxNzg1NzQ4NDQ3fQ.0sgZ50Bm93FGSTlEKN3IG7d9Uxhm7eIjqQg8p4_tiEoff8QjjZEjVb3zDw9m_rdM"
    );

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Create a capacity-2 resource as admin
        long resourceId = createResource(client, "Concurrency Test Room", 2);

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
        System.out.println("Done. Expect: 2x 201 CONFIRMED, rest 409 VERSION_CONFLICT (SLOT_FULL no longer applies here since all reads happen before any commit).");
        System.out.println();

        // 3. Staggered test: proves the SLOT_FULL -> WAITLISTED path under concurrent load,
        // not just under a single sequential request.
        runStaggeredWaitlistTest();
    }

    static long createResource(HttpClient client, String name, int capacity) throws Exception {
        String createBody = "{\"name\":\"" + name + "\",\"capacity\":" + capacity + "}";
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
        return resourceId;
    }

    static void runStaggeredWaitlistTest() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        System.out.println("=== Staggered waitlist test ===");
        long resourceId = createResource(client, "Staggered Waitlist Room", 2);

        // Wave 1: two sequential (not concurrent) requests fill the resource.
        // Each call blocks until its response returns, so by the time wave 1
        // finishes, bookedCount is genuinely committed at capacity.
        for (int i = 0; i < 2; i++) {
            String token = USER_TOKENS.get(i);
            int userIndex = i + 1;
            String bookingBody = "{\"resourceId\":" + resourceId + "}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/bookings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(bookingBody))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("Wave 1 - User " + userIndex + " -> " + resp.statusCode() + " " + resp.body());
        }
        System.out.println();

        // Wave 2: remaining 3 users fire simultaneously against an
        // already-full resource. Expect all 3 to read bookedCount == capacity
        // at the pre-check and get waitlisted directly — no version race,
        // since they never reach saveAndFlush().
        List<String> wave2Tokens = USER_TOKENS.subList(2, 5);
        int n = wave2Tokens.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch readyLatch = new CountDownLatch(n);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            String token = wave2Tokens.get(i);
            int userIndex = i + 3;
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
                    startLatch.await();

                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    System.out.println("Wave 2 - User " + userIndex + " -> " + resp.statusCode() + " " + resp.body());
                } catch (Exception e) {
                    System.out.println("Wave 2 - User " + userIndex + " -> ERROR " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        pool.shutdown();
        System.out.println();
        System.out.println("Done. Expect: all 3 wave 2 users -> 201 WAITLISTED, 0x VERSION_CONFLICT, 0x 500.");
    }
}