# CSRM data model

```mermaid
erDiagram
  APP_USERS ||--o{ BOOKINGS : reserves
  RESOURCES ||--o{ BOOKINGS : receives
  APP_USERS ||--o{ AUDIT_LOGS : performs
  APP_USERS ||--o{ NOTIFICATIONS : receives
  APP_USERS ||--o{ SERVICE_REQUESTS : submits
  APP_USERS { bigint id PK string username string password_hash string role string status string email string phone }
  RESOURCES { bigint id PK string name string type string location boolean availability decimal price }
  BOOKINGS { bigint id PK bigint user_id FK bigint resource_id FK datetime start_time datetime end_time string status bigint version }
  AUDIT_LOGS { bigint id PK bigint user_id FK string action datetime timestamp }
  NOTIFICATIONS { bigint id PK bigint user_id FK string message string delivery datetime timestamp }
  SERVICE_REQUESTS { bigint id PK bigint user_id FK string description string status datetime timestamp }
```

Relationships are validated by the service layer. IDs are stored as scalar columns; Hibernate does not create foreign-key constraints for these references. Users are retained, and resources are retired rather than deleted to preserve history.
