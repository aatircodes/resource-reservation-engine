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

    static final String ADMIN_TOKEN = "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbkByZXNlcnZhdGlvbi1lbmdpbmUubG9jYWwiLCJyb2xlIjoiQURNSU4iLCJpYXQiOjE3ODU3NjMzNzQsImV4cCI6MTc4NTg0OTc3NH0.dTaQoPDDBL7BD8s4ImlUEBQlCjDirqQwJzymWGAFkPYe8-mEpYibUOXTzLnkiLVK";

    static final List<String> USER_TOKENS = List.of(
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjFAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTc2MzM4MCwiZXhwIjoxNzg1ODQ5NzgwfQ.LeIq7a9C-TGO2QnKvaDuVbF4uTL4nbq6NzTZ9evB5S-7gfIRiA15hMq6z5UkyTix",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjJAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTc2MzM4NSwiZXhwIjoxNzg1ODQ5Nzg1fQ.OE-VfkSTIjGpXv7NMjNu4Wx9eD9HcdBZJCzWGXoDeSLNyUVfXyQaEZjySp1diil_",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjNAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTc2MzM5MCwiZXhwIjoxNzg1ODQ5NzkwfQ.pmPTHNL3fqUjEvCL-rXtoAr5dhuWEhonkYV6UsCsBBjeUJhthZREtVwdwUxjmM2r",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjRAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTc2MzM5NCwiZXhwIjoxNzg1ODQ5Nzk0fQ.p38st8JXznJm7jPkfrDN-3UWeKVY5rAGeTs1X14Zbz0cVbXBFh02i_Fuw9RfhAmK",
            "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0dXNlcjVAZXhhbXBsZS5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc4NTc2MzM5OSwiZXhwIjoxNzg1ODQ5Nzk5fQ.CJJekfEvbWObpbtMahOP8_QXyWuuEf0lp_v2KHOLw0acePZDo2hWx3NnAfa7nI48"
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

        System.out.println();
        runRetryAvailabilityTest();

        System.out.println();
        runConcurrentCancellationTest();

        System.out.println();
        runCancelWhileBookingRaceTest();
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

    static void runRetryAvailabilityTest() throws Exception {    HttpClient client = HttpClient.newHttpClient();

    System.out.println("=== Retry availability test (10 seats, 5 users) ===");
    long resourceId = createResource(client, "Retry Availability Room", 10);

    int n = USER_TOKENS.size();
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch readyLatch = new CountDownLatch(n);
    CountDownLatch startLatch = new CountDownLatch(1);
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
                startLatch.await();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                System.out.println("User " + userIndex + " -> " + resp.statusCode() + " " + resp.body());
            } catch (Exception e) {
                System.out.println("User " + userIndex + " -> ERROR " + e.getMessage());
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
    System.out.println("Done. Expect: 5x 201 CONFIRMED, 0x WAITLISTED, 0x VERSION_CONFLICT (10 seats, 5 contenders, retry lets all succeed).");
}

static void runConcurrentCancellationTest() throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    System.out.println("=== Concurrent cancellation test ===");
    long resourceId = createResource(client, "Concurrent Cancel Room", 2);

    // Book with 2 users first, capture booking IDs from each response
    List<Long> bookingIds = new java.util.ArrayList<>();
    for (int i = 0; i < 2; i++) {
        String bookingBody = "{\"resourceId\":" + resourceId + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/bookings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + USER_TOKENS.get(i))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(bookingBody))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        String idStr = resp.body().replaceAll(".*\"id\":(\\d+).*", "$1");
        bookingIds.add(Long.parseLong(idStr));
        System.out.println("Setup booking - User " + (i + 1) + " -> " + resp.statusCode() + " id=" + idStr);
    }
    System.out.println();

    // Cancel both simultaneously
    CountDownLatch readyLatch = new CountDownLatch(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);

    for (int i = 0; i < 2; i++) {
        String token = USER_TOKENS.get(i);
        Long bookingId = bookingIds.get(i);
        int userIndex = i + 1;
        pool_submit_cancel(client, token, bookingId, userIndex, readyLatch, startLatch, doneLatch);
    }

    readyLatch.await();
    startLatch.countDown();
    doneLatch.await();

    System.out.println();
    System.out.println("Done. Expect: both cancellations succeed (204), no 500s, no uncaught version conflicts.");
}

static void pool_submit_cancel(HttpClient client, String token, Long bookingId, int userIndex,
                               CountDownLatch readyLatch, CountDownLatch startLatch, CountDownLatch doneLatch) {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    pool.submit(() -> {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/bookings/" + bookingId))
                    .header("Authorization", "Bearer " + token)
                    .DELETE()
                    .build();
            readyLatch.countDown();
            startLatch.await();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("Cancel - User " + userIndex + " -> " + resp.statusCode() + " " + resp.body());
        } catch (Exception e) {
            System.out.println("Cancel - User " + userIndex + " -> ERROR " + e.getMessage());
        } finally {
            doneLatch.countDown();
        }
        pool.shutdown();
    });
}

static void runCancelWhileBookingRaceTest() throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    System.out.println("=== Cancel-while-booking race test ===");
    long resourceId = createResource(client, "Cancel Race Room", 1);

    // User 1 books (fills the only slot)
    String bookingBody = "{\"resourceId\":" + resourceId + "}";
    HttpRequest bookReq = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/bookings"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + USER_TOKENS.get(0))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofString(bookingBody))
            .build();
    HttpResponse<String> bookResp = client.send(bookReq, HttpResponse.BodyHandlers.ofString());
    String bookingIdStr = bookResp.body().replaceAll(".*\"id\":(\\d+).*", "$1");
    long bookingId = Long.parseLong(bookingIdStr);
    System.out.println("User 1 books -> " + bookResp.statusCode() + " id=" + bookingId);

    // User 2 joins waitlist
    HttpRequest waitReq = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/bookings"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + USER_TOKENS.get(1))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofString(bookingBody))
            .build();
    HttpResponse<String> waitResp = client.send(waitReq, HttpResponse.BodyHandlers.ofString());
    System.out.println("User 2 waitlists -> " + waitResp.statusCode() + " " + waitResp.body());
    System.out.println();

    // Simultaneously: User 1 cancels (triggers decrement + promotion of User 2),
    // while User 3 fires a fresh booking attempt against the same resource.
    CountDownLatch readyLatch = new CountDownLatch(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    pool.submit(() -> {
        try {
            HttpRequest cancelReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/bookings/" + bookingId))
                    .header("Authorization", "Bearer " + USER_TOKENS.get(0))
                    .DELETE()
                    .build();
            readyLatch.countDown();
            startLatch.await();
            HttpResponse<String> resp = client.send(cancelReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("User 1 cancels -> " + resp.statusCode() + " " + resp.body());
        } catch (Exception e) {
            System.out.println("Cancel -> ERROR " + e.getMessage());
        } finally {
            doneLatch.countDown();
        }
    });

    pool.submit(() -> {
        try {
            HttpRequest newBookReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/bookings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + USER_TOKENS.get(2))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(bookingBody))
                    .build();
            readyLatch.countDown();
            startLatch.await();
            HttpResponse<String> resp = client.send(newBookReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("User 3 books -> " + resp.statusCode() + " " + resp.body());
        } catch (Exception e) {
            System.out.println("Book -> ERROR " + e.getMessage());
        } finally {
            doneLatch.countDown();
        }
    });

    readyLatch.await();
    startLatch.countDown();
    doneLatch.await();
    pool.shutdown();

    System.out.println();
    System.out.println("Done. Expect: User 1 cancel succeeds, User 2 gets promoted to CONFIRMED (check via GET /api/bookings/me), User 3 gets WAITLISTED (capacity 1, already taken by promoted User 2). No 500s anywhere.");
    }
}

