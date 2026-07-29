# Design Decisions

This document explains the reasoning behind the significant architectural choices in this project, not just what was built but why. It's written to be defensible in a technical interview, not as a feature list.

## Why email as the sole user identifier

The initial design included both a username and an email field on the user account, following the pattern of an earlier project. Partway through building authentication, that assumption was revisited: this system has no public profile, no displayed handle, and no scenario where two different identifiers serve two different audiences. Email was already required and already unique. Keeping a second unique identifier that served no distinct purpose would have been carrying over a pattern without a reason specific to this project. Username was removed entirely; email is used for login, for the JWT subject claim, and for ownership checks — for example, confirming a user can only cancel their own booking.

## Why the admin account is seeded, not self-registered

This project has no tenant or organization concept — it's a single global system with one fixed ADMIN role responsible for managing resources. A "first user becomes admin" pattern makes sense in multi-tenant systems, where each tenant needs an onboarding moment and someone has to become that tenant's administrator. Here, there is no such moment: the first person to hit the registration endpoint might just be an ordinary test user, not someone who should be granted elevated privileges. Seeding the admin account via a `CommandLineRunner` at startup, with credentials supplied through environment variables, avoids a privilege-escalation window entirely and matches how a real single-tenant system's fixed operator account would typically be provisioned — created by configuration, not by a client request.

## Why registration and login are separate steps

Registration's responsibility is creating an account; login's responsibility is issuing a session credential for one that already exists. Collapsing them so that registration immediately returns a JWT would mean a single endpoint does two conceptually distinct things, which is harder to reason about and easier to get wrong as the system grows (for example, if email verification or approval steps are added later, they need to sit between account creation and first login — something a combined endpoint could not accommodate cleanly). This project keeps them distinct: registration returns a confirmation of account creation with no token; login is the only place a JWT is issued.

## Why registration never allows a client-supplied role

The registration request DTO has no role field at all — not a field that gets overwritten server-side, but one that was never added to the DTO in the first place. Every account created through self-registration is a USER, unconditionally. This is a stronger guarantee than accepting a role value and discarding or overwriting it in the service layer, since there's no code path where a client-supplied role could ever reach persistence.

## Why login failures return a generic error

When a login attempt fails, the response is the same "Invalid email or password" message regardless of whether the email doesn't exist or the password was simply wrong. Returning different messages for each case would allow an attacker to enumerate which emails have registered accounts, one request at a time. A single, generic failure message closes that off at effectively no cost to a legitimate user.

## Why `@PreAuthorize` over path-matcher-based restriction

Role-gated access could have been enforced two ways: listing role rules against URL patterns in `SecurityConfig`'s filter chain, or using `@PreAuthorize` on individual controller methods with `@EnableMethodSecurity` enabled. This project uses the latter, exclusively, for every role-gated endpoint. A path-matcher list in `SecurityConfig` grows into a separate file that has to be kept in sync with controller changes happening elsewhere, and gets harder to audit as the number of restricted routes increases. `@PreAuthorize` keeps the authorization rule on the method it protects, so the constraint is visible at the point it applies rather than cross-referenced from a different file. This was decided deliberately as a single project-wide convention rather than mixed with path-matcher rules, to avoid the two patterns coexisting and creating ambiguity about which one governs a given endpoint.

## Why booking is one slot, not a quantity

A booking request takes a `resourceId` and nothing else — there's no field for booking multiple slots at once. The domain is deliberately generic (any limited-capacity resource, not a specific booking type), and allowing a single request to claim more than one slot would mean every downstream mechanism — capacity checks, optimistic locking, waitlist position — has to account for partial-slot bookings instead of a simple one-request-one-slot model. Nothing in this project's scope calls for multi-slot bookings; if a real use case needed it, that would be a deliberate scope expansion, not a default built in speculatively.

## Why a user can only hold one active booking per resource

Testing surfaced a gap in the original happy-path implementation: nothing stopped a user from booking the same resource twice with two different idempotency keys, since idempotency keys only recognize retries of the *same* request, not repeated distinct requests. Left unaddressed, a single user could hold multiple simultaneous confirmed bookings on one resource, which doesn't match how most real booking systems behave and isn't something this project has a stated reason to allow. The fix checks, before creating a new booking, whether the requesting user already has a `CONFIRMED` booking on the same resource, and rejects the attempt with a `409 Conflict` if so. This check only applies to `CONFIRMED` bookings — a user who cancels a booking is free to rebook the same resource afterward, since cancellation is a legitimate way to release a hold on a resource, not a permanent restriction on ever booking it again.

## How idempotency keys prevent duplicate bookings on client retry

`POST /api/bookings` requires an `Idempotency-Key` header, generated by the client — a random UUID is the typical choice, though the server only requires it be a unique string per booking attempt. Its job is narrow and specific: if a client's request succeeds on the server but the response is lost before the client sees it (a dropped connection, a timeout), the client can't tell whether the booking happened. If it retries with the same key, the server recognizes it's already processed that exact key and returns the original result instead of creating a second booking. A retry with the same key is treated as "did this already happen," not as a new request.

This is a different question from whether a user should be allowed to book the same resource twice with two *different* keys — that's what the duplicate-active-booking check above enforces. The two checks run in a specific order: the idempotency-key lookup happens first, before the duplicate-booking check, so that a genuine retry of an already-successful booking is recognized and returned as-is, rather than incorrectly rejected as a duplicate booking attempt against the user's own prior success.

**Known scope boundary:** this mechanism protects against retries that happen while the client is still running (a failed request being automatically retried within the same page load). It does not, by itself, survive a literal page refresh or app relaunch before a response is received — a naive client would generate a brand-new key on reload and lose the connection to its earlier attempt. A production frontend would need to persist the pending key (e.g. to `localStorage`) before sending the request, so a reload can recover and reuse it. This project's scope is the backend guarantee — the server behaves correctly for any request carrying a previously-seen key, regardless of why the client resent it — and treats fully reload-proof client behavior as a frontend concern outside what's being demonstrated here.

## (To be added as later phases are built)

- Why optimistic locking was chosen over pessimistic locking
- How the 409 response distinguishes `SLOT_FULL` from `VERSION_CONFLICT`
- How waitlist promotion is race-free within the cancellation transaction
- Known limitations of the optimistic locking approach under extreme contention