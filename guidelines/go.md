# Go Guidelines

Source: [Google Go Style Guide](https://google.github.io/styleguide/go/)

## Style

- **Formatting:** `gofmt` / `goimports` — non-negotiable, run on save.
- **Line length:** No hard limit; keep to ~100 chars for readability.
- **Naming:**
  - Packages: short, lowercase, no underscores (`userservice`, not `user_service`)
  - Exported: `PascalCase`. Unexported: `camelCase`.
  - Acronyms: `userID`, `httpClient`, `parseURL` (all caps for acronym)
  - Receivers: short 1-2 letter abbreviation of type (`u` for `User`)
  - Interfaces: verb + `-er` when single method (`Reader`, `Writer`, `Storer`)
- **Comments:** Every exported symbol must have a doc comment starting with the symbol name.

## Error Handling

- Always handle errors. Never `_` an error from a function that can fail.
- Wrap errors with context: `fmt.Errorf("fetchUser %d: %w", id, err)`
- Sentinel errors: `var ErrNotFound = errors.New("not found")`
- Custom error types for structured errors.

```go
// Good
user, err := repo.FindByID(ctx, userID)
if err != nil {
    return fmt.Errorf("getUser %d: %w", userID, err)
}

// Bad
user, _ := repo.FindByID(ctx, userID)
```

## TDD in Go

- Test framework: standard `testing` package + `testify/assert`
- File naming: `user_service_test.go` alongside `user_service.go`
- Coverage: `go test -cover ./...` — minimum 80%

```go
func TestGetUser_NotFound_ReturnsError(t *testing.T) {
    repo := &mockUserRepo{err: ErrNotFound}
    svc := NewUserService(repo)

    _, err := svc.GetUser(context.Background(), 99)

    assert.ErrorIs(t, err, ErrNotFound)
}

func TestGetUser_Found_ReturnsUser(t *testing.T) {
    want := &User{ID: 1, Email: "a@b.com"}
    repo := &mockUserRepo{user: want}
    svc := NewUserService(repo)

    got, err := svc.GetUser(context.Background(), 1)

    require.NoError(t, err)
    assert.Equal(t, want, got)
}
```

- Table-driven tests for multiple cases:

```go
func TestValidateEmail(t *testing.T) {
    tests := []struct {
        name  string
        email string
        valid bool
    }{
        {"valid email", "a@b.com", true},
        {"missing @", "ab.com", false},
        {"empty", "", false},
    }
    for _, tc := range tests {
        t.Run(tc.name, func(t *testing.T) {
            assert.Equal(t, tc.valid, validateEmail(tc.email))
        })
    }
}
```

## Tooling

- Linter: **golangci-lint** (staticcheck, errcheck, gosec, revive)
- Format: **gofmt** + **goimports**
- Test: `go test ./...` with `-race` flag in CI
- Vulnerability scan: `govulncheck ./...`
