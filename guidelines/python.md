# Python Guidelines

Source: [Google Python Style Guide](https://google.github.io/styleguide/pyguide.html)

## Style

- **Indentation:** 4 spaces. No tabs.
- **Line length:** 80 characters max. Exception: URLs in comments.
- **Imports:** One per line. Order: stdlib → third-party → local. No wildcard imports.
- **Naming:**
  - `module_name`, `package_name`
  - `ClassName` (PascalCase)
  - `function_name`, `method_name`, `variable_name` (snake_case)
  - `CONSTANT_NAME` (SCREAMING_SNAKE_CASE)
  - `_private`, `__mangled`
- **Strings:** Prefer f-strings for formatting. Use double quotes consistently.
- **Type annotations:** Required on all public functions and methods.

```python
# Good
def calculate_total(items: list[Item], discount: float = 0.0) -> float:
    return sum(item.price for item in items) * (1 - discount)

# Bad — no types, poor naming
def calc(i, d=0):
    return sum(x.price for x in i) * (1 - d)
```

## TDD in Python

- Test framework: **pytest**
- Coverage: **pytest-cov** — minimum 80%, fail CI below this
- Mocking: **unittest.mock** — mock at external boundaries only

```python
# Naming convention
def test_calculate_total_with_discount_returns_reduced_price():
    items = [Item(price=100.0), Item(price=50.0)]
    result = calculate_total(items, discount=0.10)
    assert result == 135.0

# Fixture pattern
@pytest.fixture
def sample_user():
    return User(id=1, email="test@example.com", name="Test User")
```

- Use `pytest.mark.parametrize` for multiple input cases.
- Never use `assert` in production code for validation — raise exceptions.

## Error Handling

- Catch specific exceptions, never bare `except:`.
- Custom exceptions: inherit from appropriate base (`ValueError`, `RuntimeError`, etc.).
- Use context managers (`with`) for resources.

```python
# Good
try:
    result = api_client.fetch(user_id)
except APITimeoutError as exc:
    logger.error("API timeout for user %s: %s", user_id, exc)
    raise ServiceUnavailableError("Upstream service unavailable") from exc

# Bad
try:
    result = api_client.fetch(user_id)
except:
    pass
```

## Documentation

- Docstrings: Google style for all public modules, classes, functions.

```python
def fetch_user(user_id: int) -> User:
    """Fetches a user by ID from the database.

    Args:
        user_id: The unique identifier of the user.

    Returns:
        The User object corresponding to the given ID.

    Raises:
        UserNotFoundError: If no user exists with the given ID.
    """
```

## Tooling

- Linter: **ruff** (replaces flake8 + isort + pyupgrade)
- Type checker: **mypy** with `strict` mode
- Formatter: **ruff format** (replaces black)
- Test: **pytest** with **pytest-cov**
