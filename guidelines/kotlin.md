# Kotlin Guidelines

Source: [Google Kotlin Style Guide](https://google.github.io/styleguide/kotlinguide.html)

## Style

- **Indentation:** 4 spaces.
- **Line length:** 100 characters max.
- **Naming:**
  - `ClassName` (PascalCase)
  - `functionName`, `variableName` (camelCase)
  - `CONSTANT_NAME` (SCREAMING_SNAKE_CASE in companion objects)
  - Files: `PascalCase.kt` matching class name, or `kebab-case.kt` for utilities
- **val vs var:** Always prefer `val`. Use `var` only when mutation is required.
- **Nullability:** Design to avoid nullable types. When needed, handle explicitly — never `!!`.

## TDD in Kotlin

- Test framework: **JUnit 5** + **Kotest** (preferred) or **MockK**
- Coverage: **JaCoCo** — minimum 80%

```kotlin
// JUnit 5 + MockK style
@Test
fun `getUserById returns user when found`() {
    val expected = User(id = 1L, email = "test@example.com")
    every { userRepository.findById(1L) } returns expected

    val result = userService.getUserById(1L)

    assertThat(result).isEqualTo(expected)
}

@Test
fun `getUserById throws UserNotFoundException when not found`() {
    every { userRepository.findById(99L) } returns null

    assertThrows<UserNotFoundException> { userService.getUserById(99L) }
}
```

## Design

- Prefer data classes for DTOs and value objects.
- Use sealed classes for exhaustive when expressions.
- Extension functions: use to add behaviour to external types; avoid for internal types.
- Coroutines: always scope to the appropriate `CoroutineScope`. Never use `GlobalScope`.

## Tooling

- Linter: **ktlint** (Google ruleset)
- Format: **ktlint --format**
- Test: **JUnit 5** + **MockK** + **JaCoCo**
