# RKE Backend — Learning Notes
### Spring Boot · Spring Security · Multi-Tenancy · AOP · JPA

> These notes are based on the RKE backend codebase. Every example is taken
> directly from the actual project files.

---

## Table of Contents

1. [Java Interfaces](#1-java-interfaces)
2. [How the App Connects to the Database](#2-how-the-app-connects-to-the-database)
3. [API Design](#3-api-design)
4. [Spring Security](#4-spring-security)
5. [The 8-Step Request Security Flow](#5-the-8-step-request-security-flow)
6. [ThreadLocal — What It Is and Why It Matters](#6-threadlocal--what-it-is-and-why-it-matters)
7. [AOP — Aspect Oriented Programming](#7-aop--aspect-oriented-programming)
8. [Hibernate Tenant Filter](#8-hibernate-tenant-filter)
9. [Transactions](#9-transactions)
10. [Multithreading](#10-multithreading)
11. [End-to-End Flow: POST /api/payments/payment](#11-end-to-end-flow-post-apipaymentspayment)
12. [Error Scenarios](#12-error-scenarios)
13. [Quick Reference — Registration Map](#13-quick-reference--registration-map)

---

## 1. Java Interfaces

### What is an Interface?

An interface is a **contract**. It defines *what* methods a class must have, but
not *how* those methods work. The implementing class provides the *how*.

```
Interface = "what you must do"
Class     = "how you actually do it"
```

### Real Example — Repository Interfaces

Every repository in this project is an **interface**, not a class.

```java
// FarmerRepository.java
public interface FarmerRepository extends JpaRepository<Farmer, UUID> {

    List<Farmer> search(String name, String fatherName, UUID villageId, String mobile);

    boolean existsByTenantIdAndBillNumber(UUID tenantId, String billNumber);
}
```

```java
// TransactionRepository.java
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    BigDecimal sumGrandTotal(UUID farmerId, TransactionType type, TransactionStatus status);

    Optional<Transaction> findByBillNumber(String billNumber);
}
```

**`JpaRepository<T, ID>`** is a Spring interface that already provides:

| Method | SQL generated |
|---|---|
| `findById(id)` | `SELECT * FROM table WHERE id = ?` |
| `findAll()` | `SELECT * FROM table` |
| `save(entity)` | `INSERT` or `UPDATE` |
| `existsById(id)` | `SELECT COUNT(*) FROM table WHERE id = ?` |
| `deleteById(id)` | `DELETE FROM table WHERE id = ?` |

Spring Data JPA **auto-generates the implementation** at runtime — you never write a class.

### Why Interfaces for Repositories?

```
FarmerRepository (interface)
        ↓
Spring auto-generates: FarmerRepositoryImpl (class) at startup
        ↓
PaymentService depends on: FarmerRepository (interface type)
```

- **Loose coupling** — `PaymentService` does not care what DB is behind it
- **Swappable** — swap PostgreSQL for MongoDB by changing only the implementation
- **Testable** — inject a mock that also implements the interface; no real DB needed

```java
// PaymentService.java
// All of these are INTERFACE types — not classes
private final FarmerRepository farmerRepository;
private final TransactionRepository transactionRepository;
private final BillNumberTypeRepository billNumberTypeRepository;
```

### `UserDetails` Interface

Spring Security defines this interface to describe an authenticated user:

```java
public interface UserDetails {
    String getUsername();
    String getPassword();
    Collection<? extends GrantedAuthority> getAuthorities(); // roles
    boolean isEnabled();
    boolean isAccountNonExpired();
    boolean isAccountNonLocked();
    boolean isCredentialsNonExpired();
}
```

`StaffUserPrincipal` implements it so Spring Security can store it in the session
and use `getAuthorities()` for `@PreAuthorize` role checks:

```java
public class StaffUserPrincipal implements UserDetails, Serializable {

    private final UUID userId;
    private final UUID tenantId;
    private final StaffRole role;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        // e.g. → "ROLE_ADMIN", "ROLE_STAFF", "ROLE_SUPER_ADMIN"
    }
}
```

---

## 2. How the App Connects to the Database

The connection goes through **3 layers**:

### Layer 1 — Connection Config (`application.yml`)

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/rke}
    username: ${DB_USERNAME:rke}
    password: ${DB_PASSWORD:rke}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: none       # Hibernate does NOT touch the schema
    open-in-view: false    # no lazy loading outside transactions

  flyway:
    enabled: true
    locations: classpath:db/migration
```

Spring Boot auto-creates a **HikariCP connection pool** from these properties.

#### What is HikariCP?

Opening a DB connection is expensive (20–100 ms). HikariCP maintains a pool of
pre-opened connections (default: 10). Requests borrow a connection, use it, and
return it — without opening/closing each time.

```
Request 1 → borrows connection from pool → runs SQL → returns connection
Request 2 → borrows same connection      → runs SQL → returns connection
(no open/close overhead per request)
```

### Layer 2 — Flyway (Schema Management)

On every app startup, Flyway runs pending SQL scripts in version order:

```
V1__init.sql          → creates all tables
V2__seed_tenant.sql   → inserts RK Enterprises tenant
V3__seed_admin.sql    → inserts admin user
V4__add_return.sql    → adds column
...
V13__add_notes.sql    → latest migration
```

Flyway tracks which scripts already ran in a `flyway_schema_history` table, so
it only applies new ones. `ddl-auto: none` means Hibernate **never** alters the
schema — Flyway owns it completely.

### Layer 3 — JPA / Hibernate (Java ↔ DB Mapping)

Entities map Java classes to DB tables:

```java
// BaseEntity.java — common columns for EVERY table
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;           // → column: id

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt; // → column: created_at

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt; // → column: updated_at
}
```

```java
// Farmer.java — maps to the "farmers" table
@Entity
@Table(name = "farmers")
@Filter(name = TenantFilters.NAME)   // tenant isolation (see Section 8)
public class Farmer extends TenantScopedEntity {

    @Column(name = "name", nullable = false)
    private String name;          // → column: name

    @Column(name = "village_id", nullable = false)
    private UUID villageId;       // → column: village_id
}
```

#### Inheritance Chain

```
BaseEntity          (id, createdAt, updatedAt)
    └── TenantScopedEntity   (+ tenantId)
            └── Farmer       (+ name, villageId, address ...)
            └── Transaction  (+ farmerId, billNumber, grandTotal ...)
```

---

## 3. API Design

### The 3-Layer Pattern

Every feature follows the same structure:

```
HTTP Request
    ↓
Controller  →  receives request, validates, calls service
    ↓
Service     →  all business logic
    ↓
Repository  →  database
    ↓
HTTP Response
```

### URL Design

```java
@RestController
@RequestMapping("/api/farmers")       // base URL
public class FarmerController {

    @GetMapping                        // GET  /api/farmers
    @GetMapping("/{id}")               // GET  /api/farmers/{id}
    @PostMapping                       // POST /api/farmers
    @PutMapping("/{id}")               // PUT  /api/farmers/{id}
    @GetMapping("/{id}/balance")       // GET  /api/farmers/{id}/balance
}
```

| HTTP Method | Purpose | Status Code |
|---|---|---|
| `GET` | Read data | 200 OK |
| `POST` | Create new resource | 201 Created |
| `PUT` | Full update | 200 OK |
| `DELETE` | Remove | 204 No Content |

- `@PathVariable` → identifies a specific resource: `/api/farmers/uuid-here`
- `@RequestParam` → optional filters: `/api/farmers?name=ravi&villageId=abc`

### DTOs — Request and Response Objects

The domain entity is **never** exposed directly for writes. Separate record
classes define exactly what the API accepts and returns.

**Request DTO — with validation:**

```java
// PaymentRequest.java
public record PaymentRequest(
    @NotNull UUID farmerId,
    @NotNull UUID billNumberTypeId,
    @NotBlank String billNumber,
    @NotNull LocalDate transactionDate,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    String remarks
) {}
```

**Response DTO — controls what leaves the API:**

```java
// TransactionResponse.java
public record TransactionResponse(
    UUID id,
    String transactionNo,
    UUID farmerId,
    String billNumber,
    TransactionType transactionType,
    BigDecimal grandTotal,
    TransactionStatus status,
    List<TransactionItemResponse> items
    // tenantId is NOT here — never exposed externally
) {}
```

### `@Valid` — Automatic Validation

`@Valid` on a controller parameter fires Bean Validation before the method runs.
If any constraint fails → `400 Bad Request` automatically. Your method never executes.

```java
@PostMapping
public Farmer create(@Valid @RequestBody FarmerRequest request) {
    return service.create(request);  // only reaches here if all validations pass
}
```

### Controllers Are Always Thin

```java
// FarmerController.java — notice how simple it is
@PostMapping
public Farmer create(@Valid @RequestBody FarmerRequest request) {
    return service.create(request);  // ALL logic is in FarmerService
}
```

Zero business logic in the controller. If you add a second entry point
(scheduled job, CLI), you call the same service.

---

## 4. Spring Security

### Dependency

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### Configuration (`SecurityConfig.java`)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity    // enables @PreAuthorize on all beans
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();   // passwords always hashed with BCrypt
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository(); // session-based auth
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ...) {
        http
            .csrf(csrf -> csrf.disable())           // safe: cookie is SameSite=Lax
            .authorizeHttpRequests(reg -> reg
                .requestMatchers("/api/auth/login", "/api/health").permitAll()
                .anyRequest().authenticated())      // all other endpoints need login
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .formLogin(form -> form.disable())      // no form login
            .httpBasic(basic -> basic.disable());   // no HTTP Basic auth
        return http.build();
    }
}
```

| Setting | Decision | Why |
|---|---|---|
| Auth type | Session + cookie | Simpler than JWT for this use case |
| CSRF | Disabled | Cookie is SameSite=Lax |
| Form login | Disabled | Custom `/api/auth/login` endpoint |
| Password | BCrypt | Industry standard, one-way hash |

### Login Flow

```
Browser: POST /api/auth/login  { username, password, tenantSlug? }
    ↓
AuthService.login()
    ↓
1. staffUserRepository.findByUsername(username)
2. passwordEncoder.matches(plaintext, user.getPasswordHash())
3. StaffUserPrincipal.from(user)   → { userId, tenantId, role }
4. UsernamePasswordAuthenticationToken(principal, null, authorities)
5. securityContextRepository.saveContext(...)   → stored in HTTP session
    ↓
Browser: receives Set-Cookie: JSESSIONID=abc123
Response: UserResponse { id, tenantId, username, role }  (NO password)
```

### Logout

```java
// AuthController.java
@PostMapping("/logout")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
        session.invalidate();                   // destroy server-side session
    }
    SecurityContextHolder.clearContext();       // clear ThreadLocal too
}
```

---

## 5. The 8-Step Request Security Flow

For every secured request (e.g. `POST /api/payments/payment`):

```
1. HttpSessionSecurityContextRepository  →  who are you?
2. SecurityFilterChain                   →  are you allowed here?
3. TenantContextFilter                   →  which tenant are you?
4. TenantScopeInterceptor               →  are you in the right zone?
5. @PreAuthorize                         →  do you have the right role?
6. TenantFilterAspect                    →  lock DB queries to your tenant
7. Business logic runs
8. TenantContextFilter finally block    →  clean up ThreadLocal
```

### Step 1 — `HttpSessionSecurityContextRepository`

Reads `JSESSIONID` cookie → looks up server-side session → finds saved
`SecurityContext` → restores `StaffUserPrincipal` into `SecurityContextHolder`.

### Step 2 — `SecurityFilterChain`

Checks: is this URL in `permitAll()`?
- YES (e.g. `/api/auth/login`) → passes through
- NO → checks `SecurityContextHolder.getContext().getAuthentication().isAuthenticated()`
  - Not authenticated → `401 Unauthorized`
  - Authenticated → passes through

### Step 3 — `TenantContextFilter.doFilterInternal()`

```java
currentUserService.currentPrincipal().ifPresent(principal -> {
    if (principal.getRole() == StaffRole.SUPER_ADMIN) {
        // Check for active impersonation in session
        UUID impersonated = (UUID) session.getAttribute("IMPERSONATED_TENANT_ID");
        TenantContext.setTenantId(impersonated);  // null = cross-tenant access
    } else {
        // Normal user: lock to their own tenant
        TenantContext.setTenantId(principal.getTenantId());
    }
});
// Calls filterChain.doFilter() to pass to next step
// finally { TenantContext.clear(); }  ← always cleans up (Step 8)
```

### Step 4 — `TenantScopeInterceptor.preHandle()`

```java
// Rule 1: /api/admin/** requires SUPER_ADMIN
if (path.startsWith("/api/admin/") && role != StaffRole.SUPER_ADMIN) {
    return forbidden(response, "Super admin access required");  // 403
}

// Rule 2: all other /api/** require a tenant context
if (TenantContext.getTenantId() == null) {
    return forbidden(response, "Tenant context required.");     // 403
}
```

### Step 5 — `@PreAuthorize` (AOP)

Spring checks the annotation before the method body runs:

```java
// PaymentController.java
@PostMapping("/payment")
// No @PreAuthorize → STAFF + ADMIN can create
public TransactionResponse createPayment(...) { }

@PutMapping("/payment/{id}")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")  // STAFF gets 403
public TransactionResponse updatePayment(...) { }
```

Spring checks `principal.getAuthorities()` which returns `["ROLE_STAFF"]`,
`["ROLE_ADMIN"]`, or `["ROLE_SUPER_ADMIN"]`.

### Step 6 — `TenantFilterAspect` (AOP)

Fires automatically before every repository method call (see Section 7 & 8).

### Step 7 — Business Logic

`PaymentService`, `FarmerService`, etc. run here.

### Step 8 — `TenantContextFilter` finally block

```java
} finally {
    TenantContext.clear();
    // Thread goes back to Tomcat pool — starts completely clean
}
```

---

## 6. ThreadLocal — What It Is and Why It Matters

A `ThreadLocal<T>` holds a value **per thread** — each thread has its own
independent copy. No sharing, no locking needed.

```
Thread 1 (User A, Tenant X): TenantContext = UUID-for-tenant-X
Thread 2 (User B, Tenant Y): TenantContext = UUID-for-tenant-Y
// completely isolated — neither can see the other's value
```

Since Tomcat handles each request on its own thread, ThreadLocals ensure
per-request isolation automatically.

### Two ThreadLocals in This Project

| | ThreadLocal #1 | ThreadLocal #2 |
|---|---|---|
| **Name** | `SecurityContextHolder` | `TenantContext` |
| **Holds** | `StaffUserPrincipal` — who you are | `UUID` tenantId — which tenant |
| **Written by** | Step 1 (HttpSessionRepo) | Step 3 (TenantContextFilter) |
| **Read by** | Steps 3, 5 | Steps 4, 6 |
| **Cleared by** | Spring Security automatically | Step 8 finally block |
| **Defined in** | Spring Security internals | `TenantContext.java` |

### `TenantContextFilter` Is the Bridge

It **reads** ThreadLocal #1 and **writes** ThreadLocal #2:

```java
// Reads from SecurityContextHolder (ThreadLocal #1)
currentUserService.currentPrincipal()

// Writes to TenantContext (ThreadLocal #2)
TenantContext.setTenantId(principal.getTenantId())
```

### Why `clear()` Is Critical

Tomcat reuses threads. Without clearing:

```
Request from Tenant A → TenantContext = tenant-A-uuid
Request ends → thread goes back to pool
Next request (Tenant B) picks up same thread
TenantContext still = tenant-A-uuid  ← DATA LEAK!
```

With `finally { TenantContext.clear(); }` — the thread is always clean.

---

## 7. AOP — Aspect Oriented Programming

### What Is AOP?

AOP lets you automatically run code before or after specific methods — without
modifying those methods. You define a rule once ("run this before every
repository call") and Spring applies it everywhere.

### What Is an AOP Proxy?

At startup, Spring reads `@Aspect` classes and **wraps matching beans in proxy
objects** that look identical to the original but intercept calls.

```
Without AOP:
PaymentService → farmerRepository.findById()   (direct call)

With AOP:
PaymentService → FarmerRepositoryProxy.findById()
                    → runs TenantFilterAspect.enableTenantFilter()  ← first
                    → calls real farmerRepository.findById()         ← second
```

`PaymentService` never knows a proxy exists. It calls the interface — Spring
substitutes the proxy silently.

### `TenantFilterAspect` — Full Code

```java
// TenantFilterAspect.java
@Aspect     // this class contains AOP advice
@Component  // make it a Spring bean
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.rke.backend..repository..*(..))")
    //      ↑ fires before ANY method in ANY repository class
    public void enableTenantFilter() {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            return;  // SUPER_ADMIN cross-tenant — skip filter
        }

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(TenantFilters.NAME)
               .setParameter(TenantFilters.PARAM, tenantId);
    }
}
```

### Pointcut Expression Breakdown

```
@Before("execution(* com.rke.backend..repository..*(..))")
         ─────────  ─  ──────────────────────────  ─  ──
           when   any       package path          any  any
         method   return                         class args
           called type
```

Covers all 17 repositories:
```
FarmerRepository           ✓
TransactionRepository      ✓
BillNumberTypeRepository   ✓
VillageRepository          ✓
StaffUserRepository        ✓
TenantRepository           ✓  (fires, but filter skipped when tenantId is null)
AuditLogRepository         ✓
CottonLotRepository        ✓
... all 17                 ✓
```

### How Many Times Does It Fire?

Once per repository method call. In `PaymentService.createPayment()`:

```
farmerRepository.findById()                → aspect fires (1)
billNumberTypeRepository.findById()        → aspect fires (2)
transactionRepository.existsBy...()       → aspect fires (3)
transactionRepository.save()              → aspect fires (4)
auditLogRepository.save()                 → aspect fires (5)
```

5 repository calls → aspect fires 5 times. Each time re-enables the filter.

### `FeatureGuardAspect` — Another AOP Use

```java
// FeatureGuardAspect.java
@Before("@annotation(requiresFeature)")
public void checkFeature(RequiresFeature requiresFeature) {
    if (!tenantFeatureService.isEnabled(tenantId, requiresFeature.value())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Feature not enabled");
    }
}
```

Used like this:

```java
@GetMapping("/api/cotton-lots")
@RequiresFeature("cotton_procurement")   // 403 if this tenant doesn't have the feature
public List<CottonLot> list() { ... }
```

---

## 8. Hibernate Tenant Filter

### The Problem It Solves

Without a filter, you must manually add `AND tenant_id = ?` to **every** query:

```java
// The fragile "manual" approach — forget once → data leak
@Query("SELECT f FROM Farmer f WHERE f.name LIKE :name AND f.tenantId = :tenantId")
List<Farmer> search(@Param("name") String name, @Param("tenantId") UUID tenantId);
```

With the Hibernate filter, you define the condition **once** and it applies
everywhere automatically. Impossible to forget.

### The 3 Pieces

#### Piece 1 — `@FilterDef` in `package-info.java` (the Blueprint)

```java
// package-info.java — applies to the whole domain package
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUID.class),
    defaultCondition = "tenant_id = :tenantId"   // SQL appended to every query
)
package com.rke.backend.domain;
```

Defined once. Does nothing by itself — just declares that the filter exists.

#### Piece 2 — `@Filter` on Each Entity (the Opt-In)

```java
// Farmer.java
@Entity
@Table(name = "farmers")
@Filter(name = "tenantFilter")    // ← opts in
public class Farmer extends TenantScopedEntity { }

// Transaction.java
@Entity
@Table(name = "transactions")
@Filter(name = "tenantFilter")    // ← opts in
public class Transaction extends FinancialEntity { }

// Tenant.java
@Entity
@Table(name = "tenants")
// NO @Filter                     // ← intentionally skipped
public class Tenant extends BaseEntity { }
```

`Tenant` has no filter because you need to query all tenants during login
(e.g. look up a tenant by slug).

#### Piece 3 — `enableFilter()` in `TenantFilterAspect` (the Activation)

```java
session.enableFilter("tenantFilter")
       .setParameter("tenantId", tenantId);  // plug in the actual UUID value
```

Without this call, the filter is defined and opted-in, but **never active**.
SQL runs without the tenant clause.

### SQL Before and After

```java
// Java — same code in both cases
farmerRepository.findById(farmerId)
farmerRepository.findAll()
farmerRepository.search("ravi", null, null, null)
```

```sql
-- WITHOUT filter (TenantContext = null, e.g. SUPER_ADMIN):
SELECT * FROM farmers WHERE id = 'uuid-111'
SELECT * FROM farmers
SELECT * FROM farmers WHERE name LIKE '%ravi%'

-- WITH filter active (TenantContext = 'tenant-uuid'):
SELECT * FROM farmers WHERE id = 'uuid-111' AND tenant_id = 'tenant-uuid'
SELECT * FROM farmers WHERE tenant_id = 'tenant-uuid'
SELECT * FROM farmers WHERE name LIKE '%ravi%' AND tenant_id = 'tenant-uuid'
```

You wrote **zero** `AND tenant_id` clauses. Hibernate added them all.

### Which Entities Have the Filter?

| Entity | `@Filter`? | Reason |
|---|---|---|
| `Farmer` | ✓ Yes | Belongs to a tenant |
| `Transaction` | ✓ Yes | Belongs to a tenant |
| `Village` | ✓ Yes | Belongs to a tenant |
| `Item` | ✓ Yes | Belongs to a tenant |
| `StaffUser` | ✓ Yes | Belongs to a tenant |
| `CottonLot` | ✓ Yes | Belongs to a tenant |
| `Tenant` | ✗ No | IS the tenant table |
| `AuditLog` | ✗ No | Super admin needs cross-tenant access |

---

## 9. Transactions

### What Is `@Transactional`?

It wraps a method in a DB transaction. All DB operations inside are one atomic
unit — ALL succeed and commit together, or if any one fails, ALL roll back.

```java
// PaymentService.java
@Transactional
public TransactionResponse createPayment(PaymentRequest request, TransactionType type) {
    // All inside ONE transaction:
    farmerRepository.findById(...)          // SELECT
    billNumberTypeRepository.findById(...)  // SELECT
    transactionRepository.existsBy...()    // SELECT
    transactionRepository.save(tx)         // INSERT ← if this fails...
    auditService.record(...)               // INSERT ← this rolls back too
    // BOTH INSERTs committed together, or neither
}
```

### `@Transactional(readOnly = true)`

Used on read-only methods. Tells Hibernate "this transaction won't write" —
Hibernate can apply optimisations like skipping dirty checking.

```java
@Transactional(readOnly = true)
public BigDecimal getOutstandingBalance(UUID farmerId) {
    // only reads, no writes
}
```

### `Propagation.MANDATORY`

Means: "I must be called from within an already-open transaction. If there is
no active transaction, throw an exception immediately."

```java
// AuditService.java
@Transactional(propagation = Propagation.MANDATORY)
public void record(...) {
    auditLogRepository.save(log);
}
```

This ensures the audit log row always commits atomically with the change it
describes — enforced at compile time. Calling it without an active transaction
fails loudly during development.

---

## 10. Multithreading

### Implicit — Tomcat Thread Pool

Every HTTP request gets its own thread from Tomcat's built-in pool. You wrote
zero threading code for this — Spring Boot + Tomcat handles it.

```
User A sends request → Tomcat assigns Thread-1 → SecurityContextHolder[T1] = User A
User B sends request → Tomcat assigns Thread-2 → SecurityContextHolder[T2] = User B
(both run at the same time, completely isolated)
```

This is why ThreadLocals exist — to isolate per-request state across concurrent threads.

### Explicit — Simulation Package Only

`DbPoolExhaustionScenario` and `HistoricalIncidentScenario` use real multithreading
to deliberately exhaust the DB connection pool for incident simulation/demo purposes.
**Not real business logic.**

```java
// DbPoolExhaustionScenario.java
ExecutorService pool = Executors.newFixedThreadPool(4);
CountDownLatch allHolding = new CountDownLatch(4);
CountDownLatch releaseLatch = new CountDownLatch(1);

for (int i = 0; i < 4; i++) {
    pool.submit(() -> {
        Connection conn = dataSource.getConnection();   // grab a connection
        synchronized (heldConnections) {
            heldConnections.add(conn);                  // thread-safe list write
        }
        allHolding.countDown();    // "I'm ready"
        releaseLatch.await(...);   // hold the connection until told to release
    });
}

allHolding.await(...);             // wait until all 4 are holding
// now try one more connection → TIMES OUT → 500 error (the simulation)
```

#### Concurrency Concepts Used

| Concept | What it does |
|---|---|
| `ExecutorService` / `newFixedThreadPool(n)` | Creates and manages a fixed pool of worker threads |
| `CountDownLatch(n)` | A countdown gate — `await()` blocks until count reaches 0 via `countDown()` |
| `synchronized(list)` | Locks a block so only one thread can execute it at a time |
| `Thread.currentThread().interrupt()` | Restores the interrupted flag after catching `InterruptedException` |
| `pool.shutdownNow()` | Cancels all tasks and stops worker threads — always called in `finally` |

---

## 11. End-to-End Flow: POST /api/payments/payment

**Request:**
```
POST /api/payments/payment
Cookie: JSESSIONID=abc123
Content-Type: application/json

{
  "farmerId": "uuid-111",
  "billNumberTypeId": "uuid-222",
  "billNumber": "RKEPV-0001",
  "transactionDate": "2026-10-06",
  "amount": 5000.00
}
```

### Step-by-Step Execution

```
① HttpSessionSecurityContextRepository
   Reads JSESSIONID=abc123
   Restores StaffUserPrincipal { userId, tenantId='tenant-uuid', role=STAFF }
   into SecurityContextHolder

② SecurityFilterChain
   /api/payments/payment is NOT in permitAll()
   isAuthenticated() → YES (principal restored in ①)
   ✓ passes through

③ TenantContextFilter.doFilterInternal()
   currentUserService.currentPrincipal() → reads from SecurityContextHolder
   role == SUPER_ADMIN? → NO (role = STAFF)
   TenantContext.setTenantId('tenant-uuid')
   ThreadLocal #2 is now set

④ TenantScopeInterceptor.preHandle()
   path.startsWith('/api/admin/')? → NO
   TenantContext.getTenantId() == null? → NO ('tenant-uuid' is set)
   ✓ returns true → passes through

⑤ Controller — PaymentController.createPayment()
   @PreAuthorize? → NONE on createPayment → STAFF allowed
   @Valid → validates PaymentRequest:
     farmerId        @NotNull → present ✓
     billNumberTypeId @NotNull → present ✓
     billNumber      @NotBlank → 'RKEPV-0001' ✓
     transactionDate @NotNull → present ✓
     amount     @DecimalMin(0.01) → 5000.00 ✓
   Calls: paymentService.createPayment(request, CASH_PAYMENT)

⑥ @Transactional begins
   Spring opens a DB transaction

⑦ PaymentService.createPayment() runs:

   a) currentUserService.getTenantId()
      Reads TenantContext → 'tenant-uuid'

   b) TenantFilterAspect fires → filter enabled
      farmerRepository.findById('uuid-111')
      SQL: SELECT * FROM farmers
           WHERE id = 'uuid-111'
           AND tenant_id = 'tenant-uuid'   ← injected
      → Farmer found ✓

   c) TenantFilterAspect fires again → filter re-enabled
      billNumberTypeRepository.findById('uuid-222')
      SQL: SELECT * FROM bill_number_types
           WHERE id = 'uuid-222'
           AND tenant_id = 'tenant-uuid'   ← injected
      → BillNumberType found ✓

   d) TenantFilterAspect fires again
      transactionRepository.existsByTenantIdAndBillNumber(
          'tenant-uuid', 'RKEPV-0001'
      )
      SQL: SELECT COUNT(*) FROM transactions
           WHERE tenant_id = 'tenant-uuid'
           AND bill_number = 'RKEPV-0001'
      → 0 (not a duplicate) ✓

   e) Build Transaction entity:
      Transaction.builder()
        .tenantId('tenant-uuid')
        .farmerId('uuid-111')
        .billNumber('RKEPV-0001')
        .transactionNo(generateTransactionNo())  → '2026-RKEPV-0001-1'
        .transactionType(CASH_PAYMENT)
        .transactionDate(2026-10-06)
        .grandTotal(5000.00)
        .status(ACTIVE)
        .build()

   f) TenantFilterAspect fires
      transactionRepository.save(tx)
      SQL: INSERT INTO transactions (id, tenant_id, farmer_id,
           bill_number, transaction_no, transaction_type,
           transaction_date, grand_total, status, created_at)
           VALUES (gen_random_uuid(), 'tenant-uuid', 'uuid-111',
           'RKEPV-0001', '2026-RKEPV-0001-1', 'cash_payment',
           '2026-10-06', 5000.00, 'active', now())

   g) TenantFilterAspect fires
      auditService.record('transactions', tx.getId(), INSERT, null, snapshot)
      SQL: INSERT INTO audit_log (tenant_id, table_name, record_id,
           action, changed_by, new_values, changed_at)
           VALUES ('tenant-uuid', 'transactions', 'new-tx-uuid',
           'INSERT', 'user-uuid', '{ ...full JSON... }', now())

⑥ @Transactional commits
   Both INSERTs commit together as one DB transaction

⑧ TenantContextFilter finally block
   TenantContext.clear()
   Thread returns to Tomcat pool — clean for next request
```

**Response:** `201 Created`
```json
{
  "id": "new-tx-uuid",
  "transactionNo": "2026-RKEPV-0001-1",
  "farmerId": "uuid-111",
  "billNumber": "RKEPV-0001",
  "transactionType": "CASH_PAYMENT",
  "transactionDate": "2026-10-06",
  "grandTotal": 5000.00,
  "status": "ACTIVE",
  "items": []
}
```

---

## 12. Error Scenarios

| Where it fails | Cause | Response |
|---|---|---|
| Step ① | No cookie or expired session | `401 Unauthorized` |
| Step ② | Session exists but not authenticated | `401 Unauthorized` |
| Step ③ | SUPER_ADMIN, no impersonation active | `TenantContext = null` |
| Step ④ | Hits `/api/admin/**` without SUPER_ADMIN role | `403 Forbidden` |
| Step ④ | `TenantContext` is null on tenant endpoint | `403 Forbidden` |
| Step ⑤ | `@Valid` — amount is `-100` (fails `@DecimalMin`) | `400 Bad Request` |
| Step ⑤ | STAFF hits `PUT /api/payments/payment/{id}` | `403 Forbidden` |
| Step ⑦ | `farmerRepository.findById()` returns empty | `404 Not Found` |
| Step ⑦ | `existsByTenantIdAndBillNumber()` returns true | `409 Conflict` |
| Step ⑦ | Any DB error throws an exception | Both INSERTs rolled back, `500 Internal Server Error` |

---

## 13. Quick Reference — Registration Map

How Spring knows to run each security step in the correct order:

| Step | Component | Registered Where | Registration Mechanism |
|---|---|---|---|
| 1 | `HttpSessionSecurityContextRepository` | `SecurityConfig.java` | `@Bean` + `.securityContext(sc -> sc.securityContextRepository(repo))` |
| 2 | `SecurityFilterChain` | `SecurityConfig.java` | `http.authorizeHttpRequests(...)` |
| 3 | `TenantContextFilter` | `TenantContextFilter.java` itself | `@Component` + `extends OncePerRequestFilter` |
| 4 | `TenantScopeInterceptor` | `WebMvcConfig.java` | `registry.addInterceptor(...)` in `addInterceptors()` |
| 5 | `@PreAuthorize` | `SecurityConfig.java` | `@EnableMethodSecurity` |
| 6 | `TenantFilterAspect` | `TenantFilterAspect.java` itself | `@Aspect` + `@Component` + `@Before(...)` |
| 7 | Business logic | your code | normal method call |
| 8 | `TenantContextFilter` finally | same as step 3 | `finally { TenantContext.clear(); }` |

### Why the Order Is Guaranteed

The order is a **natural consequence** of which system each component lives in:

```
System 1 — Servlet Filters (Tomcat level)        → runs FIRST
  HttpSessionSecurityContextRepository
  SecurityFilterChain
  TenantContextFilter

System 2 — MVC Interceptors (Spring MVC level)   → runs SECOND
  TenantScopeInterceptor

System 3 — AOP (method invocation level)         → runs THIRD
  @PreAuthorize
  TenantFilterAspect
```

Tomcat processes filters before handing the request to Spring. Spring runs
interceptors before calling the controller. AOP fires at the moment specific
methods are invoked inside the controller/service.

**You never write "run step 3 after step 2"** — the order falls out naturally
from which system each class belongs to.

---

*Generated from the RKE project codebase — October 2026*
