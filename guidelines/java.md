# Java Guidelines

Source: [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

## Style

- **Indentation:** 2 spaces (Google style, not 4).
- **Line length:** 100 characters max.
- **Braces:** Always use braces for `if`, `for`, `while`, even single-line bodies.
- **Naming:**
  - `ClassName` (PascalCase)
  - `methodName`, `variableName` (camelCase)
  - `CONSTANT_NAME` (SCREAMING_SNAKE_CASE for `static final`)
  - Packages: `com.company.module.submodule` (all lowercase)
- **Annotations:** One per line, before the declaration.
- **Wildcards:** Never use wildcard imports (`import java.util.*`).

## TDD in Java

- Test framework: **JUnit 5** + **AssertJ**
- Mocking: **Mockito**
- Coverage: **JaCoCo** — minimum 80%

```java
// Naming: MethodName_Scenario_ExpectedBehavior
@Test
void getUserById_whenUserExists_returnsUser() {
    User expected = new User(1L, "test@example.com");
    when(userRepository.findById(1L)).thenReturn(Optional.of(expected));

    User result = userService.getUserById(1L);

    assertThat(result).isEqualTo(expected);
}

@Test
void getUserById_whenUserNotFound_throwsUserNotFoundException() {
    when(userRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getUserById(99L))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining("99");
}
```

## Design

- Program to interfaces. Inject dependencies via constructor (not field injection).
- Immutability: prefer `final` fields. Consider records for pure data.
- Avoid checked exceptions in new code — use unchecked exceptions.

## Tooling

- Build: **Maven** or **Gradle**
- Linter: **Checkstyle** (Google config) + **SpotBugs**
- Format: **google-java-format**
- Test: **JUnit 5** + **JaCoCo**
