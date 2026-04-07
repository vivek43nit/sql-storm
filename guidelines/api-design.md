# API Design Standards

Consistent APIs reduce cognitive load across teams. These rules apply to all HTTP APIs exposed internally or externally.

## REST URL Design

- Use plural nouns: `/users`, `/orders`, `/products`
- No verbs in URLs: ✗ `/getUser`, ✗ `/createOrder`
- Nested resources for ownership: `/users/{id}/orders`
- Max 2 levels of nesting — deeper = design smell, use query params instead
- Lowercase, hyphenated: `/payment-methods`, not `/paymentMethods`

## Versioning

- URL versioning: `/v1/users`, `/v2/users`
- Increment major version on breaking changes only
- Maintain previous version for minimum 6 months after deprecation notice
- Add `Deprecation` and `Sunset` headers on deprecated endpoints:
  ```
  Deprecation: true
  Sunset: Sat, 01 Jan 2027 00:00:00 GMT
  ```

## HTTP Methods

| Method | Use for | Idempotent | Body |
|--------|---------|-----------|------|
| GET | Read | Yes | No |
| POST | Create | No | Yes |
| PUT | Full replace | Yes | Yes |
| PATCH | Partial update | No | Yes |
| DELETE | Delete | Yes | No |

## Status Codes — Use Precisely

| Code | Meaning | When |
|------|---------|------|
| 200 | OK | Successful GET, PUT, PATCH, DELETE |
| 201 | Created | Successful POST that creates a resource |
| 204 | No Content | Successful DELETE with no response body |
| 400 | Bad Request | Client sent invalid data (validation error) |
| 401 | Unauthorised | Not authenticated — missing/invalid token |
| 403 | Forbidden | Authenticated but lacks permission |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate resource, optimistic lock failure |
| 422 | Unprocessable | Semantically invalid (field values fail business rules) |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server failure |
| 503 | Service Unavailable | Downstream dependency down |

Never return 200 with an error body. Never return 500 for client errors.

## Error Response Format

All error responses must use this structure:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "request_id": "req_abc123",
    "details": [
      {
        "field": "email",
        "code": "INVALID_FORMAT",
        "message": "Must be a valid email address."
      }
    ]
  }
}
```

- `code`: machine-readable, SCREAMING_SNAKE_CASE, stable across versions
- `message`: human-readable, safe to display
- `request_id`: always include for support traceability
- `details`: array, only present for validation errors

## Pagination

Use cursor-based pagination for large or frequently-updated datasets:

```json
{
  "data": [...],
  "pagination": {
    "next_cursor": "eyJpZCI6MTAwfQ==",
    "has_more": true,
    "limit": 20
  }
}
```

Never return unbounded lists. Default page size: 20. Max: 100.

Offset pagination is acceptable for admin UIs and small datasets (< 10K rows).

## Request / Response Conventions

- Dates: ISO 8601 UTC — `"2026-04-06T10:23:01Z"`
- IDs: strings (not integers — allows migration to UUIDs without breaking clients)
- Monetary values: integers in smallest unit (cents) — `"amount": 1999` = $19.99
- Booleans: never `0`/`1`, always `true`/`false`
- Nulls: omit optional absent fields rather than sending `null`

## Documentation

- Every endpoint must have an OpenAPI 3.0 spec
- Spec must live in the repo, not a wiki
- Generated from code annotations where possible (FastAPI, SpringDoc, tsoa)
- Include: request/response schemas, example values, error codes, authentication requirements

## Rate Limiting

- Every public endpoint must have rate limits
- Return `429` with `Retry-After` header
- Document limits in the API spec
