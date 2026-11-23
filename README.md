# Bank Account System

A complete, production-grade, functional-leaning **Bank Account System** built entirely in **Scala 3** using **sbt** and **munit**.

---

## Table of Contents
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Core Architecture & Design Decisions](#core-architecture--design-decisions)
  - [1. Domain Model & Immutability](#1-domain-model--immutability)
  - [2. Sealed Transaction Hierarchy](#2-sealed-transaction-hierarchy)
  - [3. Singleton Bank & Atomic State Transitions](#3-singleton-bank--atomic-state-transitions)
  - [4. Custom Exceptions Hierarchy](#4-custom-exceptions-hierarchy)
  - [5. Dual API: Pure Functional Either & Throwing Variant](#5-dual-api-pure-functional-either--throwing-variant)
  - [6. Atomic Transfer Guarantee](#6-atomic-transfer-guarantee)
- [How to Build, Test, and Run](#how-to-build-test-and-run)
  - [Prerequisites](#prerequisites)
  - [Compilation](#compilation)
  - [Running Unit Tests](#running-unit-tests)
  - [Running the Interactive Demo](#running-the-interactive-demo)
- [Scala-Only Verification](#scala-only-verification)

---

## Tech Stack
- **Language**: Scala 3 (`3.3.5` LTS)
- **Build Tool**: sbt (`1.10.7`)
- **Testing Framework**: `munit` (`1.0.4`)
- **Standard Library Only**: No external libraries or third-party frameworks beyond `munit` for tests.
- **Constraints**: 100% Scala source code. No Java, Python, JavaScript, Shell/Bash, or other languages.

---

## Project Structure

```
.
├── .gitignore
├── README.md
├── build.sbt
├── project
│   └── build.properties
└── src
    ├── main
    │   └── scala
    │       └── bank
    │           ├── Bank.scala                   # Singleton Bank entry point & registry
    │           ├── Main.scala                   # @main runnable demo application
    │           ├── exceptions
    │           │   └── BankException.scala      # Sealed BankException hierarchy
    │           └── model
    │               ├── Account.scala            # Account trait, SavingsAccount, CheckingAccount
    │               └── Transaction.scala        # Transaction sealed trait & case classes
    └── test
        └── scala
            └── bank
                └── BankSpec.scala               # munit test suite (47 comprehensive tests)
```

---

## Core Architecture & Design Decisions

### 1. Domain Model & Immutability
- **Arbitrary-Precision Currency**: All monetary amounts (`balance`, `amount`, `overdraftLimit`) are modeled strictly with `scala.math.BigDecimal`. Floating-point types (`Double`, `Float`) are strictly forbidden to eliminate rounding errors.
- **Precision Validation**: Monetary inputs are validated to enforce standard currency precision: strictly positive (`> 0.00`) and at most 2 decimal places.
- **Encapsulated State**: `Account` is an immutable domain model:
  - `SavingsAccount`: Does not permit overdrafts. Balance can never fall below `0.00`.
  - `CheckingAccount`: Supports a configurable `overdraftLimit: BigDecimal >= 0.00`. Balances can dip into negative territory down to `-overdraftLimit`.
- **Account Status**: Accounts track operational lifecycle (`AccountStatus.Active` vs `AccountStatus.Closed`). Operating on a closed account is rejected with `InvalidOperationException`. Closing an account requires a zero balance.

### 2. Sealed Transaction Hierarchy
Transactions are modeled as an immutable Algebraic Data Type (ADT):
```scala
sealed trait Transaction:
  def id: String
  def amount: BigDecimal
  def timestamp: Instant
  def involvedAccountIds: Set[String]
  def description: String
```
Concrete case classes:
- `Transaction.Deposit(id, accountId, amount, timestamp)`
- `Transaction.Withdrawal(id, accountId, amount, timestamp)`
- `Transaction.Transfer(id, sourceAccountId, targetAccountId, amount, timestamp)`

Every transaction is timestamped (UTC) and recorded chronologically in the involved accounts' immutable audit history.

### 3. Singleton Bank & Atomic State Transitions
- `object Bank` is the single entry point coordinating accounts, generating unique IDs (`ACC-1001`, `TXN-5001`), and maintaining the registry.
- **Thread Safety**: Backed by `java.util.concurrent.atomic.AtomicReference[Map[String, Account]]`.
- **Lock-Free Concurrency**: All state updates use a tail-recursive Compare-And-Set (`compareAndSet`) retry loop. State transitions are purely functional and atomic.

### 4. Custom Exceptions Hierarchy
All domain failures extend `BankException`:
- `InsufficientFundsException(accountId, balance, requestedAmount)`: Thrown when balance is insufficient (e.g. savings account withdrawal beyond balance).
- `OverdraftLimitExceededException(accountId, balance, overdraftLimit, requestedAmount)`: Thrown when checking account withdrawal exceeds available balance + overdraft limit.
- `InvalidAmountException(amount, reason)`: Thrown on zero, negative, or excessive decimal precision amounts.
- `AccountNotFoundException(accountId)`: Thrown when an account ID cannot be found.
- `DuplicateAccountException(accountId)`: Thrown when attempting to register an existing account ID.
- `InvalidOperationException(message)`: Thrown for illegal business operations (self-transfer, operating on closed account, closing non-zero account).

### 5. Dual API: Pure Functional Either & Throwing Variant
Every banking operation exposes two symmetric interfaces:
1. **Safe Functional API** (`*Safe`): Returns `Either[BankException, T]`.
   - `depositSafe`, `withdrawSafe`, `transferSafe`, `closeAccountSafe`, `getAccountSafe`, etc.
2. **Throwing API**: Unwraps the `Either` and throws the domain exception on failure.
   - `deposit`, `withdraw`, `transfer`, `closeAccount`, `getAccount`, etc.

This gives callers full freedom to choose idiomatic functional composition via `for`-comprehensions or exception-driven workflows.

### 6. Atomic Transfer Guarantee
Transfers between accounts are strictly atomic. Before applying any balance changes:
1. Inputs are validated (`source != target`, `amount > 0`, scale <= 2).
2. Both accounts are verified to exist and be active.
3. Outbound debit validation is verified against available funds/overdraft.
4. Inbound credit is verified.
5. If **any** check fails, neither account is debited or credited.

---

## How to Build, Test, and Run

### Prerequisites
- JDK 17+ (or JDK 21/24)
- sbt 1.9+ or 1.10+

### Compilation
Compile all Scala sources with strict warnings enabled:
```sh
sbt compile
```

### Running Unit Tests
Execute the 47 munit test suites covering all edge cases, exceptions, overdraft logic, atomicity, and transaction history:
```sh
sbt test
```

### Running the Interactive Demo
Run the `@main` application to see formatted demonstrations of account opening, deposits, withdrawals, atomic transfers, checking overdrafts, and every custom exception being caught and handled:
```sh
sbt run
```

---

## Scala-Only Verification

All source files, test suites, and build scripts in this repository are written exclusively in Scala:

| File | Type | Description |
|------|------|-------------|
| `build.sbt` | Scala DSL | sbt project build configuration |
| `src/main/scala/bank/model/Account.scala` | Scala 3 | Immutable Account domain model & logic |
| `src/main/scala/bank/model/Transaction.scala` | Scala 3 | Sealed Transaction ADT |
| `src/main/scala/bank/exceptions/BankException.scala` | Scala 3 | Sealed BankException hierarchy |
| `src/main/scala/bank/Bank.scala` | Scala 3 | Bank singleton registry & atomic operations |
| `src/main/scala/bank/Main.scala` | Scala 3 | Demo application entry point (`@main`) |
| `src/test/scala/bank/BankSpec.scala` | Scala 3 | Unit test suite using `munit` |
| `project/build.properties` | Config | Declares `sbt.version=1.10.7` |
| `README.md` | Markdown | Documentation |
| `.gitignore` | Config | Git ignore rules |

No Java, Kotlin, Python, JavaScript, Bash scripts, or other languages exist in the repository.
