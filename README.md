# Bank Account System (Scala)

A bank account system written entirely in **Scala 3**. It models accounts as classes, transactions as immutable case classes, and the bank itself as a singleton object. Invalid operations and overdrafts are handled with a custom exception hierarchy.

![Scala](https://img.shields.io/badge/Scala-3-red)
![sbt](https://img.shields.io/badge/build-sbt-blue)
![Tests](https://img.shields.io/badge/tests-munit-green)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

## Features

- **Account types**
  - `SavingsAccount`: no overdraft allowed
  - `CheckingAccount`: configurable overdraft limit
- **Transactions as case classes**: a sealed `Transaction` trait with `Deposit`, `Withdrawal` and `Transfer`, all immutable and recorded in each account's history
- **Singleton `Bank` object**: one entry point to open and close accounts, look them up, deposit, withdraw, transfer and fetch statements
- **Atomic transfers**: if any step fails, neither account changes
- **Custom exceptions** with clear, descriptive messages
- **Precise money handling**: `BigDecimal` is used for all amounts, never `Double`
- **100% Scala**: source, tests and build config contain no other languages

## Custom Exceptions

All exceptions extend a sealed `BankException` base class.

| Exception | Raised when |
|---|---|
| `InsufficientFundsException` | A withdrawal or transfer exceeds the available balance |
| `OverdraftLimitExceededException` | A checking account goes past its overdraft limit |
| `InvalidAmountException` | The amount is zero, negative or has invalid precision |
| `AccountNotFoundException` | The account id does not exist |
| `DuplicateAccountException` | An account with the same id already exists |
| `InvalidOperationException` | Transfer to the same account, operating on a closed account, or closing an account with a non-zero balance |

## Project Structure

```
bank-account-system/
├── build.sbt
├── project/
│   └── build.properties
├── src/
│   ├── main/scala/bank/
│   │   ├── model/          # Account types and Transaction case classes
│   │   ├── exceptions/     # BankException hierarchy
│   │   ├── Bank.scala      # Singleton bank object
│   │   └── Main.scala      # @main demo
│   └── test/scala/bank/    # munit test suites
├── .gitignore
└── README.md
```

## Getting Started

### Prerequisites

- JDK 17 or newer
- [sbt](https://www.scala-sbt.org/)

### Clone and build

```bash
git clone https://github.com/<your-username>/bank-account-system.git
cd bank-account-system
sbt compile
```

### Run the demo

```bash
sbt run
```

The demo opens accounts, makes deposits, withdrawals and a transfer, uses an overdraft on a checking account, and triggers each custom exception.

### Run the tests

```bash
sbt test
```

## Usage Example

```scala
import bank.Bank

val savings  = Bank.openSavingsAccount(owner = "Asha", initialDeposit = BigDecimal(1000))
val checking = Bank.openCheckingAccount(owner = "Ravi", initialDeposit = BigDecimal(500), overdraftLimit = BigDecimal(200))

Bank.deposit(savings.id, BigDecimal(250))
Bank.withdraw(checking.id, BigDecimal(600))        // allowed: within overdraft limit
Bank.transfer(savings.id, checking.id, BigDecimal(300))

// Safe variant that returns Either instead of throwing
Bank.safeWithdraw(savings.id, BigDecimal(99999)) match
  case Left(err)  => println(s"Failed: ${err.getMessage}")
  case Right(acc) => println(s"New balance: ${acc.balance}")

Bank.statement(savings.id).foreach(println)
```

> The method names above are illustrative. Adjust them to match your implementation.

## Design Decisions

- **Immutability first**: transactions are case classes and balances change only through validated operations.
- **Encapsulation**: account state cannot be modified directly from outside the class.
- **Validate before mutating**: every input is checked before any state changes, which keeps failed operations from leaving partial updates.
- **Two error styles**: the domain layer throws custom exceptions, and the `Bank` object offers `Either`-based variants for callers who prefer functional error handling.
- **Singleton bank**: `object Bank` owns the account registry and generates unique ids.

## Testing

Tests are written with [munit](https://scalameta.org/munit/) and cover:

- Successful deposits, withdrawals and transfers
- Overdraft allowed within the limit and rejected beyond it
- Every custom exception
- Transfer atomicity on failure
- Transaction history correctness
- Edge cases: zero or negative amounts, closed accounts and unknown ids

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes and make sure `sbt test` passes
4. Open a pull request

Please keep all contributions in Scala.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
