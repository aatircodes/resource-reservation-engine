# Design Decisions

This document explains the reasoning behind the significant architectural choices in this project, not just what was built but why. It's written to be defensible in a technical interview, not as a feature list.

## Why email as the sole user identifier

The initial design included both a username and an email field on the user account, following the pattern of an earlier project. Partway through building authentication, that assumption was revisited: this system has no public profile, no displayed handle, and no scenario where two different identifiers serve two different audiences. Email was already required and already unique. Keeping a second unique identifier that served no distinct purpose would have been carrying over a pattern without a reason specific to this project. Username was removed entirely; email is used for login, for the JWT subject claim, and for any future ownership checks (for example, confirming a user can only cancel their own booking).

## Why the admin account is seeded, not self-registered

This project has no tenant or organization concept — it's a single global system with one fixed ADMIN role responsible for managing resources. A "first user becomes admin" pattern makes sense in multi-tenant systems, where each tenant needs an onboarding moment and someone has to become that tenant's administrator. Here, there is no such moment: the first person to hit the registration endpoint might just be an ordinary test user, not someone who should be granted elevated privileges. Seeding the admin account via a `CommandLineRunner` at startup, with credentials supplied through environment variables, avoids a privilege-escalation window entirely and matches how a real single-tenant system's fixed operator account would typically be provisioned — created by configuration, not by a client request.

## Why registration and login are separate steps

Registration's responsibility is creating an account; login's responsibility is issuing a session credential for one that already exists. Collapsing them so that registration immediately returns a JWT would mean a single endpoint does two conceptually distinct things, which is harder to reason about and easier to get wrong as the system grows (for example, if email verification or approval steps are added later, they need to sit between account creation and first login — something a combined endpoint could not accommodate cleanly). This project keeps them distinct: registration returns a confirmation of account creation with no token; login is the only place a JWT is issued.

## Why registration never allows a client-supplied role

The registration request DTO has no role field at all — not a field that gets overwritten server-side, but one that was never added to the DTO in the first place. Every account created through self-registration is a USER, unconditionally. This is a stronger guarantee than accepting a role value and discarding or overwriting it in the service layer, since there's no code path where a client-supplied role could ever reach persistence.

## Why login failures return a generic error

When a login attempt fails, the response is the same "Invalid email or password" message regardless of whether the email doesn't exist or the password was simply wrong. Returning different messages for each case would allow an attacker to enumerate which emails have registered accounts, one request at a time. A single, generic failure message closes that off at effectively no cost to a legitimate user.

## (To be added as later phases are built)

- Why optimistic locking was chosen over pessimistic locking
- How idempotency keys prevent duplicate bookings on client retry
- How waitlist promotion is race-free within the cancellation transaction
- Known limitations of the optimistic locking approach under extreme contention