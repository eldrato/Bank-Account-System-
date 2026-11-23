package bank

import munit.FunSuite
import bank.exceptions.*
import bank.model.*

/** Comprehensive test suite for the Bank Account System using munit. */
class BankSpec extends FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    Bank.reset()

  // ==========================================================================
  // Account Creation Tests
  // ==========================================================================

  test("SavingsAccount creation with zero initial deposit") {
    val acc = Bank.openSavingsAccount("Alice")
    assertEquals(acc.ownerName, "Alice")
    assertEquals(acc.balance, BigDecimal(0))
    assertEquals(acc.status, AccountStatus.Active)
    assertEquals(acc.transactionHistory, Nil)
    assertEquals(acc.accountType, "Savings")
  }

  test("SavingsAccount creation with valid positive initial deposit") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("250.75"))
    assertEquals(acc.balance, BigDecimal("250.75"))
    assertEquals(acc.transactionHistory.size, 1)
    val tx = acc.transactionHistory.head
    assert(tx.isInstanceOf[Transaction.Deposit])
    assertEquals(tx.amount, BigDecimal("250.75"))
  }

  test("CheckingAccount creation with overdraft limit and initial deposit") {
    val acc = Bank.openCheckingAccount("Bob", BigDecimal("100.00"), BigDecimal("500.00"))
    assertEquals(acc.ownerName, "Bob")
    assertEquals(acc.balance, BigDecimal("100.00"))
    assertEquals(acc.overdraftLimit, BigDecimal("500.00"))
    assertEquals(acc.accountType, "Checking")
    assertEquals(acc.transactionHistory.size, 1)
  }

  test("Account creation fails with negative initial deposit (InvalidAmountException)") {
    intercept[InvalidAmountException] {
      Bank.openSavingsAccount("Alice", BigDecimal("-10.00"))
    }
  }

  test("CheckingAccount creation fails with negative overdraft limit (InvalidAmountException)") {
    intercept[InvalidAmountException] {
      Bank.openCheckingAccount("Bob", BigDecimal("100.00"), BigDecimal("-50.00"))
    }
  }

  test("Account creation fails with invalid precision (scale > 2)") {
    intercept[InvalidAmountException] {
      Bank.openSavingsAccount("Alice", BigDecimal("100.555"))
    }
    intercept[InvalidAmountException] {
      Bank.openCheckingAccount("Bob", BigDecimal("100.00"), BigDecimal("200.123"))
    }
  }

  test("Account creation fails with duplicate account ID (DuplicateAccountException)") {
    Bank.openSavingsAccountWithId("ACC-UNIQUE", "Alice")
    intercept[DuplicateAccountException] {
      Bank.openSavingsAccountWithId("ACC-UNIQUE", "Bob")
    }
    intercept[DuplicateAccountException] {
      Bank.openCheckingAccountWithId("ACC-UNIQUE", "Charlie")
    }
  }

  test("Account creation fails with blank or empty owner name (InvalidOperationException)") {
    intercept[InvalidOperationException] {
      Bank.openSavingsAccount("")
    }
    intercept[InvalidOperationException] {
      Bank.openSavingsAccount("   ")
    }
  }

  // ==========================================================================
  // Deposit Tests
  // ==========================================================================

  test("Successful deposit increases balance and records Deposit transaction") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    val tx = Bank.deposit(acc.id, BigDecimal("50.25"))

    assertEquals(tx.amount, BigDecimal("50.25"))
    assertEquals(tx.accountId, acc.id)

    val updated = Bank.getAccount(acc.id)
    assertEquals(updated.balance, BigDecimal("150.25"))
    assertEquals(updated.transactionHistory.size, 2)
    assertEquals(updated.transactionHistory.last, tx)
  }

  test("Multiple deposits accumulate correctly in history") {
    val acc = Bank.openSavingsAccount("Alice")
    Bank.deposit(acc.id, BigDecimal("10.00"))
    Bank.deposit(acc.id, BigDecimal("20.00"))
    Bank.deposit(acc.id, BigDecimal("30.00"))

    val updated = Bank.getAccount(acc.id)
    assertEquals(updated.balance, BigDecimal("60.00"))
    assertEquals(updated.transactionHistory.size, 3)
    assertEquals(updated.transactionHistory.map(_.amount), List(BigDecimal("10.00"), BigDecimal("20.00"), BigDecimal("30.00")))
  }

  test("Deposit fails on zero amount (InvalidAmountException)") {
    val acc = Bank.openSavingsAccount("Alice")
    intercept[InvalidAmountException] {
      Bank.deposit(acc.id, BigDecimal("0.00"))
    }
  }

  test("Deposit fails on negative amount (InvalidAmountException)") {
    val acc = Bank.openSavingsAccount("Alice")
    intercept[InvalidAmountException] {
      Bank.deposit(acc.id, BigDecimal("-50.00"))
    }
  }

  test("Deposit fails on bad precision (scale > 2) (InvalidAmountException)") {
    val acc = Bank.openSavingsAccount("Alice")
    intercept[InvalidAmountException] {
      Bank.deposit(acc.id, BigDecimal("15.999"))
    }
  }

  test("Deposit fails on unknown account ID (AccountNotFoundException)") {
    intercept[AccountNotFoundException] {
      Bank.deposit("ACC-DOES-NOT-EXIST", BigDecimal("100.00"))
    }
  }

  test("Deposit fails on closed account (InvalidOperationException)") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("0.00"))
    Bank.closeAccount(acc.id)

    intercept[InvalidOperationException] {
      Bank.deposit(acc.id, BigDecimal("50.00"))
    }
  }

  // ==========================================================================
  // Withdrawal & Overdraft Tests
  // ==========================================================================

  test("Successful withdrawal decreases balance and records Withdrawal transaction") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("200.00"))
    val tx = Bank.withdraw(acc.id, BigDecimal("75.50"))

    assertEquals(tx.amount, BigDecimal("75.50"))
    assertEquals(tx.accountId, acc.id)

    val updated = Bank.getAccount(acc.id)
    assertEquals(updated.balance, BigDecimal("124.50"))
    assertEquals(updated.transactionHistory.size, 2)
    assertEquals(updated.transactionHistory.last, tx)
  }

  test("Savings account allows withdrawing entire balance to exactly 0.00") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    Bank.withdraw(acc.id, BigDecimal("100.00"))

    val updated = Bank.getAccount(acc.id)
    assertEquals(updated.balance, BigDecimal("0.00"))
  }

  test("Savings account rejects withdrawal exceeding balance (InsufficientFundsException)") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("50.00"))
    val ex = intercept[InsufficientFundsException] {
      Bank.withdraw(acc.id, BigDecimal("50.01"))
    }
    assertEquals(ex.accountId, acc.id)
    assertEquals(ex.balance, BigDecimal("50.00"))
    assertEquals(ex.requestedAmount, BigDecimal("50.01"))
  }

  test("Checking account allows withdrawal beyond balance within overdraft limit") {
    val acc = Bank.openCheckingAccount("Bob", BigDecimal("100.00"), BigDecimal("200.00"))
    val tx = Bank.withdraw(acc.id, BigDecimal("250.00"))

    assertEquals(tx.amount, BigDecimal("250.00"))
    val updated = Bank.getAccount(acc.id)
    assertEquals(updated.balance, BigDecimal("-150.00"))
  }

  test("Checking account allows withdrawal to exact overdraft boundary") {
    val acc = Bank.openCheckingAccount("Bob", BigDecimal("50.00"), BigDecimal("150.00"))
    Bank.withdraw(acc.id, BigDecimal("200.00"))

    val updated = Bank.getAccount(acc.id)
    assertEquals(updated.balance, BigDecimal("-150.00"))
  }

  test("Checking account rejects withdrawal exceeding overdraft limit (OverdraftLimitExceededException)") {
    val acc = Bank.openCheckingAccount("Bob", BigDecimal("50.00"), BigDecimal("150.00"))
    val ex = intercept[OverdraftLimitExceededException] {
      Bank.withdraw(acc.id, BigDecimal("200.01"))
    }
    assertEquals(ex.accountId, acc.id)
    assertEquals(ex.balance, BigDecimal("50.00"))
    assertEquals(ex.overdraftLimit, BigDecimal("150.00"))
    assertEquals(ex.requestedAmount, BigDecimal("200.01"))
  }

  test("Withdrawal fails on zero or negative amount (InvalidAmountException)") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    intercept[InvalidAmountException] {
      Bank.withdraw(acc.id, BigDecimal("0.00"))
    }
    intercept[InvalidAmountException] {
      Bank.withdraw(acc.id, BigDecimal("-25.00"))
    }
  }

  test("Withdrawal fails on bad precision (InvalidAmountException)") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    intercept[InvalidAmountException] {
      Bank.withdraw(acc.id, BigDecimal("10.001"))
    }
  }

  test("Withdrawal fails on unknown account ID (AccountNotFoundException)") {
    intercept[AccountNotFoundException] {
      Bank.withdraw("ACC-UNKNOWN", BigDecimal("10.00"))
    }
  }

  test("Withdrawal fails on closed account (InvalidOperationException)") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("0.00"))
    Bank.closeAccount(acc.id)
    intercept[InvalidOperationException] {
      Bank.withdraw(acc.id, BigDecimal("10.00"))
    }
  }

  // ==========================================================================
  // Transfer Tests
  // ==========================================================================

  test("Successful transfer debits source and credits target") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("500.00"))
    val bob = Bank.openCheckingAccount("Bob", BigDecimal("100.00"), BigDecimal("200.00"))

    val tx = Bank.transfer(alice.id, bob.id, BigDecimal("150.00"))
    assertEquals(tx.sourceAccountId, alice.id)
    assertEquals(tx.targetAccountId, bob.id)
    assertEquals(tx.amount, BigDecimal("150.00"))

    val updatedAlice = Bank.getAccount(alice.id)
    val updatedBob = Bank.getAccount(bob.id)

    assertEquals(updatedAlice.balance, BigDecimal("350.00"))
    assertEquals(updatedBob.balance, BigDecimal("250.00"))

    // Verify transaction recorded in both histories
    assertEquals(updatedAlice.transactionHistory.last, tx)
    assertEquals(updatedBob.transactionHistory.last, tx)
  }

  test("Transfer using checking account overdraft succeeds") {
    val bob = Bank.openCheckingAccount("Bob", BigDecimal("50.00"), BigDecimal("200.00"))
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("0.00"))

    Bank.transfer(bob.id, alice.id, BigDecimal("200.00"))

    assertEquals(Bank.getAccount(bob.id).balance, BigDecimal("-150.00"))
    assertEquals(Bank.getAccount(alice.id).balance, BigDecimal("200.00"))
  }

  test("Transfer to the same account fails (InvalidOperationException)") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    intercept[InvalidOperationException] {
      Bank.transfer(alice.id, alice.id, BigDecimal("25.00"))
    }
  }

  test("Transfer fails when source account has insufficient funds (InsufficientFundsException)") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("50.00"))
    val bob = Bank.openSavingsAccount("Bob", BigDecimal("50.00"))

    intercept[InsufficientFundsException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("100.00"))
    }
  }

  test("Transfer fails when checking source account exceeds overdraft limit (OverdraftLimitExceededException)") {
    val bob = Bank.openCheckingAccount("Bob", BigDecimal("50.00"), BigDecimal("100.00"))
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("0.00"))

    intercept[OverdraftLimitExceededException] {
      Bank.transfer(bob.id, alice.id, BigDecimal("150.01"))
    }
  }

  test("Transfer fails when source account does not exist (AccountNotFoundException)") {
    val bob = Bank.openSavingsAccount("Bob", BigDecimal("100.00"))
    intercept[AccountNotFoundException] {
      Bank.transfer("ACC-NONEXISTENT", bob.id, BigDecimal("50.00"))
    }
  }

  test("Transfer fails when target account does not exist (AccountNotFoundException)") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    intercept[AccountNotFoundException] {
      Bank.transfer(alice.id, "ACC-NONEXISTENT", BigDecimal("50.00"))
    }
  }

  test("Transfer fails when source account is closed (InvalidOperationException)") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("0.00"))
    val bob = Bank.openSavingsAccount("Bob", BigDecimal("100.00"))
    Bank.closeAccount(alice.id)

    intercept[InvalidOperationException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("10.00"))
    }
  }

  test("Transfer fails when target account is closed (InvalidOperationException)") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    val bob = Bank.openSavingsAccount("Bob", BigDecimal("0.00"))
    Bank.closeAccount(bob.id)

    intercept[InvalidOperationException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("10.00"))
    }
  }

  test("Transfer fails on zero, negative or bad precision amounts (InvalidAmountException)") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    val bob = Bank.openSavingsAccount("Bob", BigDecimal("100.00"))

    intercept[InvalidAmountException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("0.00"))
    }
    intercept[InvalidAmountException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("-10.00"))
    }
    intercept[InvalidAmountException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("10.555"))
    }
  }

  // ==========================================================================
  // Transfer Atomicity Tests
  // ==========================================================================

  test("Transfer atomicity: source account is untouched when target account does not exist") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("500.00"))

    intercept[AccountNotFoundException] {
      Bank.transfer(alice.id, "ACC-MISSING", BigDecimal("100.00"))
    }

    val aliceAfter = Bank.getAccount(alice.id)
    assertEquals(aliceAfter.balance, BigDecimal("500.00"))
    assertEquals(aliceAfter.transactionHistory.size, 1) // only initial deposit
  }

  test("Transfer atomicity: source account is untouched when target account is closed") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("500.00"))
    val bob = Bank.openSavingsAccount("Bob", BigDecimal("0.00"))
    Bank.closeAccount(bob.id)

    intercept[InvalidOperationException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("100.00"))
    }

    val aliceAfter = Bank.getAccount(alice.id)
    assertEquals(aliceAfter.balance, BigDecimal("500.00"))
    assertEquals(aliceAfter.transactionHistory.size, 1)
  }

  test("Transfer atomicity: target account is untouched when source has insufficient funds") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("50.00"))
    val bob = Bank.openSavingsAccount("Bob", BigDecimal("200.00"))

    intercept[InsufficientFundsException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("100.00"))
    }

    val aliceAfter = Bank.getAccount(alice.id)
    val bobAfter = Bank.getAccount(bob.id)

    assertEquals(aliceAfter.balance, BigDecimal("50.00"))
    assertEquals(bobAfter.balance, BigDecimal("200.00"))
    assertEquals(aliceAfter.transactionHistory.size, 1)
    assertEquals(bobAfter.transactionHistory.size, 1)
  }

  test("Transfer atomicity: neither account changes on invalid amount") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("500.00"))
    val bob = Bank.openSavingsAccount("Bob", BigDecimal("200.00"))

    intercept[InvalidAmountException] {
      Bank.transfer(alice.id, bob.id, BigDecimal("-50.00"))
    }

    assertEquals(Bank.getAccount(alice.id).balance, BigDecimal("500.00"))
    assertEquals(Bank.getAccount(bob.id).balance, BigDecimal("200.00"))
  }

  // ==========================================================================
  // Account Closing Tests
  // ==========================================================================

  test("Closing account with zero balance succeeds and marks status Closed") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("0.00"))
    val closed = Bank.closeAccount(acc.id)

    assertEquals(closed.status, AccountStatus.Closed)
    assert(closed.isClosed)
    assert(!closed.isOpen)
    assertEquals(Bank.getAccount(acc.id).status, AccountStatus.Closed)
  }

  test("Closing account with positive balance fails (InvalidOperationException)") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("150.00"))
    intercept[InvalidOperationException] {
      Bank.closeAccount(acc.id)
    }
  }

  test("Closing checking account with negative balance fails (InvalidOperationException)") {
    val acc = Bank.openCheckingAccount("Bob", BigDecimal("50.00"), BigDecimal("100.00"))
    Bank.withdraw(acc.id, BigDecimal("100.00")) // balance is now -50.00

    intercept[InvalidOperationException] {
      Bank.closeAccount(acc.id)
    }
  }

  test("Closing an already-closed account fails (InvalidOperationException)") {
    val acc = Bank.openSavingsAccount("Alice", BigDecimal("0.00"))
    Bank.closeAccount(acc.id)

    intercept[InvalidOperationException] {
      Bank.closeAccount(acc.id)
    }
  }

  test("Closing non-existent account fails (AccountNotFoundException)") {
    intercept[AccountNotFoundException] {
      Bank.closeAccount("ACC-DOES-NOT-EXIST")
    }
  }

  // ==========================================================================
  // Transaction History & Statement Tests
  // ==========================================================================

  test("Transaction history preserves chronological order and accurate details") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    val bob = Bank.openCheckingAccount("Bob", BigDecimal("50.00"), BigDecimal("100.00"))

    val d1 = Bank.deposit(alice.id, BigDecimal("50.00"))
    val w1 = Bank.withdraw(alice.id, BigDecimal("25.00"))
    val t1 = Bank.transfer(alice.id, bob.id, BigDecimal("40.00"))

    val history = Bank.getTransactionHistory(alice.id)
    assertEquals(history.size, 4) // initial deposit + 3 operations
    assertEquals(history(1), d1)
    assertEquals(history(2), w1)
    assertEquals(history(3), t1)

    // Transaction involved account IDs
    assertEquals(t1.involvedAccountIds, Set(alice.id, bob.id))
    assertEquals(d1.involvedAccountIds, Set(alice.id))
    assertEquals(w1.involvedAccountIds, Set(alice.id))
  }

  test("Statement generation outputs correct header, details, and transaction count") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("200.00"))
    Bank.deposit(alice.id, BigDecimal("100.00"))

    val statement = Bank.getStatement(alice.id)
    assertEquals(statement.account.id, alice.id)
    assertEquals(statement.transactions.size, 2)

    val formatted = statement.formatted
    assert(formatted.contains("ACCOUNT STATEMENT"))
    assert(formatted.contains(alice.id))
    assert(formatted.contains("Alice"))
    assert(formatted.contains("$300.00"))
    assert(formatted.contains("TRANSACTION HISTORY (2 records)"))
  }

  // ==========================================================================
  // Safe API Tests (Either[BankException, A])
  // ==========================================================================

  test("Safe methods return Right on valid inputs and Left with BankException on errors") {
    val alice = Bank.openSavingsAccount("Alice", BigDecimal("100.00"))
    val bob = Bank.openCheckingAccount("Bob", BigDecimal("50.00"), BigDecimal("100.00"))

    // Safe deposit
    assert(Bank.depositSafe(alice.id, BigDecimal("50.00")).isRight)
    assert(Bank.depositSafe(alice.id, BigDecimal("-50.00")).isLeft)

    // Safe withdraw
    assert(Bank.withdrawSafe(alice.id, BigDecimal("20.00")).isRight)
    assert(Bank.withdrawSafe(alice.id, BigDecimal("5000.00")).isLeft)

    // Safe transfer
    assert(Bank.transferSafe(alice.id, bob.id, BigDecimal("30.00")).isRight)
    assert(Bank.transferSafe(alice.id, bob.id, BigDecimal("5000.00")).isLeft)
    assert(Bank.transferSafe(alice.id, alice.id, BigDecimal("10.00")).isLeft)

    // Safe getAccount
    assert(Bank.getAccountSafe(alice.id).isRight)
    assert(Bank.getAccountSafe("ACC-UNKNOWN").isLeft)

    // Safe closeAccount
    assert(Bank.closeAccountSafe(alice.id).isLeft) // non-zero balance
  }
