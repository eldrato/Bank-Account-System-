package bank

import bank.exceptions.*
import bank.model.*
import bank.server.BankWebServer

/** Demo and local server entry point.
  *
  * - 'sbt run' starts the localhost HTTP server on http://localhost:8080 and displays the demo
  * - 'sbt "run demo"' runs just the console demo
  * - 'sbt "run server"' starts just the HTTP server
  */
@main def run(args: String*): Unit =
  val mode = args.headOption.getOrElse("both")

  if mode == "demo" then
    runConsoleDemo()
  else
    val port = 8080
    // Seed initial accounts
    BankWebServer.seedDemoData()

    val server = new BankWebServer(port)
    server.start()

    println(
      s"""================================================================================
         |              BANK ACCOUNT SYSTEM - LOCALHOST SERVER ACTIVE
         |================================================================================
         |  HTTP Server running at : http://localhost:$port
         |  Interactive Web UI     : http://localhost:$port/
         |  REST API               : http://localhost:$port/api/accounts
         |  Health Check           : http://localhost:$port/api/health
         |
         |  Sample accounts for Alice Smith (Savings), Bob Jones (Checking), and
         |  Charlie Brown (Checking) are pre-loaded.
         |================================================================================""".stripMargin
    )

    if mode != "server" then
      runConsoleDemo()
      println(
        s"""--------------------------------------------------------------------------------
           | Localhost server is active at http://localhost:$port
           | Open http://localhost:$port in your browser to manage accounts and transactions.
           | Press Ctrl+C in your terminal to shut down the server.
           |--------------------------------------------------------------------------------""".stripMargin
      )

    // Keep server process running
    Thread.currentThread().join()

/** Executes the full console demo. */
def runConsoleDemo(): Unit =
  println(
    """================================================================================
      |                   BANK ACCOUNT SYSTEM DEMONSTRATION
      |================================================================================""".stripMargin
  )

  // Ensure clean state for demo
  Bank.reset()

  // 1. Opening Accounts
  printSectionHeader("1. Opening Accounts")

  val aliceSavings = Bank.openSavingsAccount(
    ownerName = "Alice Smith",
    initialDeposit = BigDecimal("500.00")
  )
  println(s"Opened Savings Account  : ID=${aliceSavings.id}, Owner='${aliceSavings.ownerName}', Balance=${BankException.formatCurrency(aliceSavings.balance)}")

  val bobChecking = Bank.openCheckingAccount(
    ownerName = "Bob Jones",
    initialDeposit = BigDecimal("200.00"),
    overdraftLimit = BigDecimal("300.00")
  )
  println(s"Opened Checking Account : ID=${bobChecking.id}, Owner='${bobChecking.ownerName}', Balance=${BankException.formatCurrency(bobChecking.balance)}, OverdraftLimit=${BankException.formatCurrency(bobChecking.overdraftLimit)}")

  val charlieChecking = Bank.openCheckingAccount(
    ownerName = "Charlie Brown",
    initialDeposit = BigDecimal("0.00"),
    overdraftLimit = BigDecimal("100.00")
  )
  println(s"Opened Checking Account : ID=${charlieChecking.id}, Owner='${charlieChecking.ownerName}', Balance=${BankException.formatCurrency(charlieChecking.balance)}, OverdraftLimit=${BankException.formatCurrency(charlieChecking.overdraftLimit)}")

  // 2. Deposits & Withdrawals
  printSectionHeader("2. Deposits & Withdrawals")

  val dep1 = Bank.deposit(aliceSavings.id, BigDecimal("250.00"))
  println(s"Executed: ${dep1.description}")
  println(s"Alice's updated balance: ${BankException.formatCurrency(Bank.getAccount(aliceSavings.id).balance)}")

  val with1 = Bank.withdraw(aliceSavings.id, BigDecimal("100.00"))
  println(s"Executed: ${with1.description}")
  println(s"Alice's updated balance: ${BankException.formatCurrency(Bank.getAccount(aliceSavings.id).balance)}")

  val dep2 = Bank.deposit(bobChecking.id, BigDecimal("50.00"))
  println(s"Executed: ${dep2.description}")
  println(s"Bob's updated balance: ${BankException.formatCurrency(Bank.getAccount(bobChecking.id).balance)}")

  // 3. Atomic Funds Transfer
  printSectionHeader("3. Atomic Funds Transfer")

  val transferAmount = BigDecimal("200.00")
  println(s"Initiating atomic transfer of ${BankException.formatCurrency(transferAmount)} from Alice (${aliceSavings.id}) to Bob (${bobChecking.id})...")
  val xfer = Bank.transfer(aliceSavings.id, bobChecking.id, transferAmount)
  println(s"Transfer completed successfully: ${xfer.description}")
  println(s"Alice's balance after transfer: ${BankException.formatCurrency(Bank.getAccount(aliceSavings.id).balance)}")
  println(s"Bob's balance after transfer  : ${BankException.formatCurrency(Bank.getAccount(bobChecking.id).balance)}")

  // 4. Checking Account Overdraft Feature
  printSectionHeader("4. Checking Account Overdraft Feature")

  val currentBob = Bank.getAccount(bobChecking.id).asInstanceOf[CheckingAccount]
  println(s"Bob's current balance is ${BankException.formatCurrency(currentBob.balance)} with overdraft limit ${BankException.formatCurrency(currentBob.overdraftLimit)}.")
  println(s"Total available funds: ${BankException.formatCurrency(currentBob.balance + currentBob.overdraftLimit)}")

  val overdraftWithdrawal = BigDecimal("600.00")
  println(s"Bob withdraws ${BankException.formatCurrency(overdraftWithdrawal)} (exceeds balance of ${BankException.formatCurrency(currentBob.balance)}, dipping into overdraft)...")
  val with2 = Bank.withdraw(bobChecking.id, overdraftWithdrawal)
  println(s"Executed: ${with2.description}")
  val bobAfterOverdraft = Bank.getAccount(bobChecking.id).asInstanceOf[CheckingAccount]
  println(s"Bob's balance is now NEGATIVE: ${BankException.formatCurrency(bobAfterOverdraft.balance)}")
  println(s"Remaining overdraft allowance: ${BankException.formatCurrency(bobAfterOverdraft.balance + bobAfterOverdraft.overdraftLimit)}")

  // 5. Custom Exceptions Demonstration
  printSectionHeader("5. Custom Exceptions Demonstration")

  testScenario("5.1 InsufficientFundsException (Savings Account)") {
    val aliceBal = Bank.getAccount(aliceSavings.id).balance
    println(s"Alice's balance is ${BankException.formatCurrency(aliceBal)}. Attempting withdrawal of $$1,000.00...")
    Bank.withdraw(aliceSavings.id, BigDecimal("1000.00"))
  }

  testScenario("5.2 OverdraftLimitExceededException (Checking Account)") {
    val bobBal = Bank.getAccount(bobChecking.id).balance
    println(s"Bob's balance is ${BankException.formatCurrency(bobBal)} with overdraft limit $$300.00 ($$150.00 available). Attempting withdrawal of $$200.00...")
    Bank.withdraw(bobChecking.id, BigDecimal("200.00"))
  }

  testScenario("5.3a InvalidAmountException (Negative Amount)") {
    println("Attempting deposit of -$50.00...")
    Bank.deposit(aliceSavings.id, BigDecimal("-50.00"))
  }

  testScenario("5.3b InvalidAmountException (Zero Amount)") {
    println("Attempting withdrawal of $0.00...")
    Bank.withdraw(aliceSavings.id, BigDecimal("0.00"))
  }

  testScenario("5.3c InvalidAmountException (Bad Precision > 2 Decimal Places)") {
    println("Attempting deposit of $10.555 (fractional cents)...")
    Bank.deposit(aliceSavings.id, BigDecimal("10.555"))
  }

  testScenario("5.4 AccountNotFoundException (Non-existent ID)") {
    println("Attempting lookup of account 'ACC-9999'...")
    Bank.getAccount("ACC-9999")
  }

  testScenario("5.5 DuplicateAccountException (Duplicate Account Registration)") {
    println(s"Attempting to register account with already taken ID '${aliceSavings.id}'...")
    Bank.openSavingsAccountWithId(aliceSavings.id, "Imposter Alice")
  }

  testScenario("5.6a InvalidOperationException (Self-Transfer)") {
    println(s"Attempting transfer from Alice (${aliceSavings.id}) to Alice (${aliceSavings.id})...")
    Bank.transfer(aliceSavings.id, aliceSavings.id, BigDecimal("50.00"))
  }

  testScenario("5.6b InvalidOperationException (Closing Account With Non-Zero Balance)") {
    val bal = Bank.getAccount(aliceSavings.id).balance
    println(s"Alice's balance is ${BankException.formatCurrency(bal)}. Attempting to close account...")
    Bank.closeAccount(aliceSavings.id)
  }

  testScenario("5.6c InvalidOperationException (Operating On Closed Account)") {
    println(s"Closing Charlie's zero-balance account (${charlieChecking.id})...")
    Bank.closeAccount(charlieChecking.id)
    println(s"Account ${charlieChecking.id} successfully closed. Now attempting to deposit $$50.00 into it...")
    Bank.deposit(charlieChecking.id, BigDecimal("50.00"))
  }

  // 6. Statement Generation
  printSectionHeader("6. Formatted Statements & Audit Log")

  println(Bank.getStatement(aliceSavings.id).formatted)
  println()
  println(Bank.getStatement(bobChecking.id).formatted)

  // Seed demo data again so localhost web UI has initial accounts ready
  BankWebServer.seedDemoData()

private def testScenario(title: String)(block: => Any): Unit =
  println(s"--> Scenario: $title")
  try
    val result = block
    println(s"    [UNEXPECTED SUCCESS]: $result")
  catch
    case ex: BankException =>
      println(s"    [CAUGHT EXPECTED EXCEPTION]: ${ex.getClass.getSimpleName}")
      println(s"    Message: ${ex.getMessage}")
    case other: Throwable =>
      println(s"    [UNEXPECTED EXCEPTION]: ${other.getClass.getName}: ${other.getMessage}")
  println()

private def printSectionHeader(title: String): Unit =
  println()
  println(s"--------------------------------------------------------------------------------")
  println(s" $title")
  println(s"--------------------------------------------------------------------------------")
