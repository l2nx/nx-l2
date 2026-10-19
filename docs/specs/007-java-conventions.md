# 007. Java conventions: Lombok, naming, records, logging

## 1. Decision

- **The whole tree follows this canon:** `libs`, `login`, every chronicle, in every source set (`main`, `test`,
  `testFixtures`, `jmh`). New code follows it from the start; the imported Mobius code was converted in one pass, not
  file by file as it is touched.
- **Lombok replaces hand-written boilerplate under an allow-list**; everything outside the list fails compilation
  through `lombok.config` or the ArchUnit test (§7).
- **Fields follow standard Java naming.** No `_` prefix; a boolean field carries no `is`.
- **A data carrier is a `record`**, never `@Value`. A record with two or more components carries
  `@Builder(toBuilder = true)`.
- **Logging is SLF4J through `@Slf4j`.** The project's code does not use `java.util.logging` (JUL).

Issues: [#14](https://github.com/l2nx/nx-l2/issues/14) (SLF4J), [#15](https://github.com/l2nx/nx-l2/issues/15)
(Lombok).

## 2. Why

The imported Mobius tree carried about 2,800 trivial getters, 500 trivial setters, about 300 hand-declared JUL
loggers with 1,850 calls, and a field naming of its own. The boilerplate hides the code that matters and drifts when
fields change. The Mobius code logged through JUL with string concatenation, so every disabled log line still built
its string; JUL's own `{0}` MessageFormat parameters are clumsy, and its level names (`severe`, `fine`, `finest`)
match nothing else on the stack. The rest of the platform already writes Lombok, records and `@Slf4j`; one canon
means one way to read the code.

Rejected:

- **File-by-file conversion as code is touched:** the tree stays mixed for years, and every functional diff carries
  unrelated churn.
- **Keeping `_field` and `lombok.accessors.prefix += _`:** a non-standard name plus a Lombok setting every
  contributor trips over, kept only to shrink one diff.
- **`@Value` for immutable classes:** Java 25 has records; `@Value` is a second way to say the same thing.
- **Logging through `@Log` (JUL) or `@Log4j2`:** JUL keeps the clumsy API above; Log4j2 is the backend, not the
  API, and the platform codes against SLF4J.

## 3. Lombok

**Allowed:**

- **`@Getter` / `@Setter` on fields**, not on the class: a reader sees which field has an accessor, and a class does
  not grow setters for every field. They replace only trivial accessors: a body of exactly `return field;` or
  `this.field = value;`, not `synchronized`, no copy, no side effect, with the name Lombok generates. A non-public
  accessor keeps its visibility through `AccessLevel`. Any other accessor stays hand-written, and so does one that
  shares a name with another method of the class: Lombok then generates nothing.
- **`@Slf4j`** as the only logger of a class (§6).
- **`@RequiredArgsConstructor`:**
  - on a Spring bean, as `spring-architecture` prescribes for constructor injection, with its two traps: Lombok
    does not copy `@Qualifier` to the constructor parameter, and a `@Configuration` class takes its dependencies as
    `@Bean` method parameters instead;
  - on any other class only where the constructor does nothing but assign fields and no two parameters of one type
    stand next to each other (a swapped call compiles).
- **`@Builder(toBuilder = true)`** on records (§5).
- **`@ToString` / `@EqualsAndHashCode` on classes, never on records**, with `onlyExplicitlyIncluded = true` and the
  members marked `@ToString.Include` / `@EqualsAndHashCode.Include`. A subclass calls the superclass method
  (`lombok.config`). Value equality is not given to a mutable class whose instances serve as map keys or set
  elements. A hand-written method stays when the generated one would differ from it.

**Forbidden** (`flagUsage = ERROR`): `@Data`, `@Value`, `@AllArgsConstructor`, `@NoArgsConstructor`,
`@Synchronized` and `@Locked` (hidden lock objects), `@Getter(lazy = true)`, `@SneakyThrows`, `@Cleanup`
(try-with-resources), `val` / `var` (Java `var`), Lombok `@NonNull` (nullability annotations are a separate
decision), `@With`, `@SuperBuilder`, every `lombok.experimental` feature (`@UtilityClass`, `@Delegate`,
`@FieldDefaults`, `@Accessors`, …), every logger annotation except `@Slf4j`. `@With` and `@SuperBuilder` have left
`experimental` and carry keys of their own.

`@Builder` on a class is forbidden too. `flagUsage` cannot tell a record from a class, so ArchUnit checks it (§7).
The other limits of this section are invisible in bytecode, because Lombok annotations have source retention; they
are held by review.

`lombok.config` at the repository root:

```
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
lombok.toString.onlyExplicitlyIncluded = true
lombok.toString.callSuper = call
lombok.equalsAndHashCode.callSuper = call
lombok.data.flagUsage = ERROR
lombok.value.flagUsage = ERROR
lombok.allArgsConstructor.flagUsage = ERROR
lombok.noArgsConstructor.flagUsage = ERROR
lombok.synchronized.flagUsage = ERROR
lombok.locked.flagUsage = ERROR
lombok.getter.lazy.flagUsage = ERROR
lombok.with.flagUsage = ERROR
lombok.superBuilder.flagUsage = ERROR
lombok.sneakyThrows.flagUsage = ERROR
lombok.cleanup.flagUsage = ERROR
lombok.val.flagUsage = ERROR
lombok.var.flagUsage = ERROR
lombok.nonNull.flagUsage = ERROR
lombok.experimental.flagUsage = ERROR
lombok.log.javaUtilLogging.flagUsage = ERROR
lombok.log.log4j.flagUsage = ERROR
lombok.log.log4j2.flagUsage = ERROR
lombok.log.apacheCommons.flagUsage = ERROR
lombok.log.jbosslog.flagUsage = ERROR
lombok.log.flogger.flagUsage = ERROR
lombok.log.xslf4j.flagUsage = ERROR
lombok.log.custom.flagUsage = ERROR
```

`callSuper = call` applies only to a class that extends something. `lombok.Generated` marks generated members, so
coverage tools skip them and ArchUnit can find generated builders.

## 4. Naming

- **No `_` prefix** on any field, whatever its visibility. A parameter or local that shadows a field is reached
  through `this.`.
- **A field is named after its accessor** when the two differ: `getAttacker()` reads `attacker`, so `@Getter`
  covers it and the public API does not change. An accessor outside Lombok's pattern (`hasJoinedGs()`,
  `allowPeaceAttack()`) keeps its name and stays hand-written.
- **A boolean field has no `is` prefix:** `active`, and Lombok generates `isActive()` for a primitive `boolean` (a
  `Boolean` field gets `getActive()`, so a flag is primitive unless `null` means something). A Mobius `_isActive`
  becomes `active`; its accessor keeps its name.
- **A name that would be a Java keyword gets a meaningful one** (`intStat`, `protectedItem`, `classIds`), not a
  mangled spelling.
- Constants (`static final`) keep `UPPER_SNAKE_CASE`.

## 5. Records

- **A record is a data carrier only.** A class becomes one when:
  - it has at least one field, and every field is final and assigned straight from a constructor parameter (a
    derived value becomes a method);
  - every component is immutable or defensively copied, so value `equals`, `hashCode` and `toString` never walk
    live state. A reference to a game entity (a `WorldObject`, a `Skill`) counts as immutable: it compares by the
    entity's identity and prints a short label, which is what an event record needs;
  - it neither extends a class nor is extended, is not a Spring bean and not a `getInstance()` singleton;
  - every field may become a public accessor, and no accessor implements an interface method under a `get` name.

  A class that fails a condition keeps `@Getter`. Buffers, crypto holders and handlers with behaviour stay classes.

- **Equality and text are checked before conversion.** A record compares by value, a class by identity, and a
  record prints its components. A class converts only when its instances do not rely on identity: `HashSet` / `Set.of`
  elements and `HashMap` / `Map.of` keys (`Set.of` and `Map.of` throw on equal duplicates), `contains`, `indexOf`,
  `remove(Object)`, `distinct()`, `Collectors.toMap`, `Objects.equals`, `==`, and no hot path pays for a value
  `hashCode`. Array components compare by reference, so they count as identity. A hand-written `equals`, `hashCode`
  or `toString` that differs from the record's is kept explicitly; a `toString` that reaches packets, HTML or an
  audit channel is checked.
- **Accessors are `x()`**, as the language defines; callers in `server`, `scripts` and tests change with the record.
  This changes the API that forks consume (spec 001 §4.1); accepted before the first scripts release
  ([#11](https://github.com/l2nx/nx-l2/issues/11)).
- **`@Builder(toBuilder = true)` on every record with two or more components**, as across the platform. A
  constructor call with three or more arguments, or with two arguments of one type, uses the builder.
- **A record loaded from a `StatSet`** gets a static factory `of(StatSet set)` that calls the builder; the canonical
  constructor stays the only constructor.
- `@Builder.Default` does not work on records; a default is a static factory.

## 6. Logging

- **Each class that writes to the log declares its own logger:** `@Slf4j`, field `log`. No logger is inherited or
  shared: no `public` or `protected` logger, no instance logger, no logger field in an interface (an interface field
  is public; `@Slf4j` is illegal there). A default interface method or a base-class method that must name the
  concrete class calls `LoggerFactory.getLogger(getClass())` at the call site.
- **Inner, local and anonymous classes log through their enclosing class:** Lombok does not put `@Slf4j` on a
  non-static nested class, so the logger belongs to the nearest enclosing top-level, `static` nested, enum or record
  class.
- **Channels are explicit fields of the class that writes to them:**
  `private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting")`. The channel names and their
  files are spec 002 §6.
- **Messages use `{}` placeholders**, never string concatenation. A `Throwable` goes last, without a placeholder.
- **No class-name prefix in the message** (`getClass().getSimpleName() + ": "`): the layout prints the logger name,
  which is the writing class because every class has its own logger.
- **JUL level mapping:** `severe` → `error`, `warning` → `warn`, `info` and `config` → `info`, `fine` → `debug`,
  `finer` and `finest` → `trace`.
- **JDK platform logging** (`System.Logger`, which defaults to JUL) reaches Log4j2 through the `Log4jBridgeHandler`
  that Spring Boot's `Log4J2LoggingSystem` installs; `spring-boot-starter-log4j2` brings `log4j-jul` for it. Boot
  skips the handler while `java.util.logging.manager` names the Log4j `LogManager`, so the project sets no such flag
  and declares no `log4j-jul` of its own (spec 002 §2, §6). A JUL record emitted before Boot initialises logging
  goes to the JDK console handler; no project code does that.

## 7. Enforcement

- `lombok.config` (§3) fails compilation on a forbidden annotation.
- `JavaConventionsTest` (`libs/boot` test fixtures) is extended in `login/boot` and in each chronicle's `boot`, the
  only modules that see `server` and `scripts` together; it imports every `app.l2nx` main class on the test
  classpath. The test, `testFixtures` and `jmh` sources are held by review. It asserts:
  - no class depends on `java.util.logging`;
  - a field of type `org.slf4j.Logger` is `private static final` and named either `log` (the `@Slf4j` field) or
    `*_LOG` (a channel); no interface declares one;
  - a type that declares a static `builder()` method returning a Lombok-generated builder (a nested type whose
    members carry `lombok.Generated`) is a record.

## 8. Policy

- **Blame:** bulk conversion commits stay in `git blame`; the repository keeps no ignore list (spec 005 §5).
- **Licensing:** converting code to this canon (renames, Lombok, logging API, records) is a mechanical change in the
  sense of spec 001 §2, like the package rename and the reformat: a converted Mobius file gets no
  `L2NX contributors` block and keeps its license.
- **A defect found during conversion** that the conversion does not fix is filed as a Bug issue (repository
  `CLAUDE.md`), not fixed in passing.

## 9. Build

- `gradle/libs.versions.toml`: version and library `lombok`; libraries `slf4j-api` (version from the Spring Boot
  BOM) and `archunit-junit5`; no `log4j-jul` entry.
- `nx-l2.java-conventions` adds Lombok as `compileOnly` and as the annotation processor of every source set
  (`annotationProcessor`, `testAnnotationProcessor`, `testFixturesAnnotationProcessor`, `jmhAnnotationProcessor`),
  and `slf4j-api` as `implementation`: `@Slf4j` needs it at compile time in every module, not only in `boot`.
  Lombok is not on any runtime classpath, so the `scripts` artifact carries no Lombok dependency.
