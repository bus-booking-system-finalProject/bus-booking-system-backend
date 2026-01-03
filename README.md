# Bus Booking System Backend

Bus Booking System is a microservices-based architecture designed to handle the core logic of a bus reservation platform. It utilizes the Spring Boot ecosystem to provide a scalable, robust, and secure environment for managing users, bus schedules, and bookings.

Key architectural features include centralized configuration, service discovery, and an API Gateway for routing and security.

## Full project source code

| Component        | GitHub Repository  |
|------------------|--------------------|
| Backend          | [nghiaz160904/bus-booking-system-backend](https://github.com/nghiaz160904/bus-booking-system-backend) |
| Frontend         | [nghiaz160904/bus-booking-system-frontend](https://github.com/nghiaz160904/bus-booking-system-frontend) |

---

## 🚀 Getting Started

Follow these instructions to get the project up and running on your local machine.

### Prerequisites

* Java 25
* Docker & Docker Compose

### 1. Build the projects

Navigate to the root directory and build the JAR files, skipping tests for a faster build:

```bash
./gradlew build -x test
```

### 2. Run with Docker

Once the build is complete, orchestrate the containers using Docker Compose:

1 Firstly, run the core infrastructure: database, redis service

```bash
docker-compose --profile infra up -d
```

2 Start: config-server, service-registry, api-gateway

```bash
docker-compose --profile core up -d
```

3 Wait about 1 or 2 minutes for the 'core' completely starts, then run the main application: booking-service, user-service

```bash
docker-compose --profile apps up -d
```

## 🔗 URLs & Services

Once the application is running, the services can be accessed at the following endpoints:

| Service          | Production  | Development                                    |
|------------------|-------------|------------------------------------------------|
| config-server    | TBD         | [http://localhost:8888](http://localhost:8888) |
| api-gateway      | TBD         | [http://localhost:8080](http://localhost:8080) |
| service-registry | TBD         | [http://localhost:8081](http://localhost:8081) |
| user-service     | TBD         | [http://localhost:8082](http://localhost:8082) |

## Authentication and authorization design

The system implements a robust security mechanism using JSON Web Tokens (JWT) combined with HttpOnly Cookies and database persistence for session management.

### 1. The Architecture

* **Access Token (JWT):**

  * **Purpose:** Used to authenticate API requests to microservices.
  * **Storage:** Stored in client memory (variable) or non-HttpOnly storage.
  * **Lifespan:** Short-lived (15 minutes).
  * **Payload:** Contains User ID and `Roles` (User, Admin).

* **Refresh Token:**

  * **Purpose:** Used to generate new Access Tokens when the current one expires without requiring the user to log in again.

  * **Storage:** Sent to the client in a secure HttpOnly Cookie.

  * **Persistence:** A hash of the refresh token is stored in the Database associated with the user.

  * **Lifespan:** Long-lived (7 days).

### 2. Role-Based Access Control (RBAC)

Authorization is handled via scopes/claims embedded directly in the JWT Access Token.

* `ROLE_USER`: Can search buses, book tickets, and view their own profile.

* `ROLE_ADMIN`: Can manage bus routes, schedules, and view all user data.

The API Gateway inspects the JWT header to allow or deny traffic based on these roles before the request reaches the downstream services.

### 3. Decisions and Trade-offs

We chose this hybrid approach (Stateless Access Token + Stateful Refresh Token) to balance security and performance.

| Decision | Implementation | Trade-off / Rationale |
| :--- | :--- | :--- |
| **HttpOnly Cookies** | The Refresh Token is strictly stored in an `HttpOnly`, `Secure` cookie. | **Why:** This prevents Cross-Site Scripting (XSS) attacks. JavaScript cannot read this cookie, making it significantly harder for attackers to steal the user's session. |
| **Database Storage** | We store the Refresh Token status in the database. | **Why:** This allows for **Token Revocation**. If a user's account is compromised or they click "Log Out on all devices," we can simply delete/invalidate the token in the DB, instantly cutting off access. `<br><br>` **Trade-off:** It adds a database look-up overhead during the token refresh process, but increases security control. |
| **JWT for Access** | Access Tokens are stateless JWTs. | **Why:** Microservices (User Service, Booking Service) can validate the token signature independently without needing to query a central database for every single API request, reducing latency. |

### 4. Session Flow Explanation

#### 1. The Login Flow (Initialization)

This happens when the user first enters their credentials.

**1. Request:** The Client sends `username` and `password` to the Auth Service

**2. Validation:** The Service validates credentials against the Database

**3. Token Creation:**

* Access Token (AT): Generated with User ID and Roles (e.g., `ROLE_USER`).
* Refresh Token (RT): A random secure string is generated.

**4. Persistence (Crucial Step):** The Service hashes the Refresh Token and stores it in the Database linked to that user

**5. Response:**

* The Access Token is sent in the JSON Response body (so the Javascript client can put it in memory).
* The Refresh Token is sent in a `Set-Cookie` header with flags `HttpOnly; Secure; SameSite`.

#### 2. The Authenticated Request Flow (Happy Path)

This is the standard flow for the first 15 minutes (while the Access Token is valid).

**1. Request:** The User wants to "Book a Ticket." The Client attaches the Access Token to the `Authorization: Bearer <token>` header

**2. Gateway Interception:** The API Gateway intercepts the request

**3. Stateless Verification:**

* The Gateway validates the JWT signature (using the public key or shared secret).
* It checks the `exp` (expiration) claim.
* RBAC Check: It checks if the payload contains `ROLE_USER`.

**4. Forwarding:** Since the token is valid, the Gateway forwards the request to the **Booking Microservice**.

**5. Response:** The Microservice processes the booking and returns data.

* Note: The Database is **not** queried for authentication here, ensuring high performance.

#### 3. The Refresh Flow (Silent Re-Auth)

This happens automatically when the Access Token expires (after 15 minutes).

**1. Failed Request:** The Client sends a request with an expired Access Token.

**2. Rejection:** The Gateway detects the expiration and returns `401 Unauthorized`.

**3. Refresh Trigger:** The Client logic detects the `401` and calls the `/refresh` endpoint.

**4. Cookie Transmission:** The Browser automatically includes the **HttpOnly Cookie** (containing the Refresh Token) in this request. The Client JavaScript cannot touch this.

**5. Stateful Verification:**

* The Auth Service receives the cookie.
* It hashes the Refresh Token and looks it up in the **Database**.

**6. Revocation Check:**

* **If found:** The session is valid. The Service generates a **New Access Token**.
* **If NOT found (or revoked):** The Service rejects the request (forcing the user to log in again).

**7. Response:** The New Access Token is sent to the Client. The user continues their session uninterrupted.

#### 4. The Logout / Revocation Flow

This creates the security benefit of the "Database Storage" decision.

**1. Request:** User clicks "Log Out."

**2. DB Cleanup:** The Service deletes the Refresh Token record from the Database.

**3. Cookie Cleanup:** The Service sends a response to clear/expire the HttpOnly cookie.

**4. Immediate Effect:** Even if an attacker managed to steal the Refresh Token cookie earlier, it is now useless because the **database lookup** in Step 3 (Refresh Flow) will fail.
