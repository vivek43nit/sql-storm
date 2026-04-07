# Rust Guidelines

Source: [Rust API Guidelines](https://rust-lang.github.io/api-guidelines/) + [Rust Style Guide](https://doc.rust-lang.org/nightly/style-guide/)

## Style

- **Formatting:** `rustfmt` — non-negotiable, run on save.
- **Naming:**
  - `StructName`, `EnumName`, `TraitName` (PascalCase)
  - `function_name`, `variable_name`, `module_name` (snake_case)
  - `CONSTANT_NAME` (SCREAMING_SNAKE_CASE)
  - Lifetimes: short lowercase (`'a`, `'b`)
- **Clippy:** Zero warnings. Run `cargo clippy -- -D warnings` in CI.

## Error Handling

- Return `Result<T, E>` from fallible functions. Never `unwrap()` in production code.
- Define custom error types with `thiserror` crate.
- Propagate with `?` operator.

```rust
use thiserror::Error;

#[derive(Debug, Error)]
pub enum UserError {
    #[error("user {0} not found")]
    NotFound(u64),
    #[error("database error: {0}")]
    Database(#[from] sqlx::Error),
}

pub async fn get_user(id: u64, pool: &PgPool) -> Result<User, UserError> {
    sqlx::query_as!(User, "SELECT * FROM users WHERE id = $1", id as i64)
        .fetch_optional(pool)
        .await?
        .ok_or(UserError::NotFound(id))
}
```

## TDD in Rust

- Use built-in `#[test]` and `#[cfg(test)]` module in same file.
- Integration tests: `tests/` directory at crate root.
- Coverage: **cargo-tarpaulin** — minimum 80%

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_email_with_valid_input_returns_true() {
        assert!(validate_email("user@example.com"));
    }

    #[test]
    fn test_validate_email_without_at_symbol_returns_false() {
        assert!(!validate_email("notanemail"));
    }

    // Async tests
    #[tokio::test]
    async fn test_get_user_not_found_returns_error() {
        let pool = test_db().await;
        let result = get_user(99999, &pool).await;
        assert!(matches!(result, Err(UserError::NotFound(99999))));
    }
}
```

## Tooling

- Format: **rustfmt**
- Lint: **cargo clippy** with `-D warnings`
- Test: `cargo test`
- Coverage: **cargo-tarpaulin**
- Security: **cargo audit** (dependency vulnerability scan)
