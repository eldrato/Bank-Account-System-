package bank

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import bank.exceptions.*
import bank.model.*

/** Formatted account statement snapshot.
  *
  * @param account the account state at generation time
  * @param generatedAt UTC timestamp when the statement was generated
  * @param transactions recorded transactions for the account
  */
final case class AccountStatement(
    account: Account,
    generatedAt: Instant,
    transactions: List[Transaction]
):
  /** Generates a human-readable, beautifully formatted statement suitable for console output. */
  def formatted: String =
    val overdraftStr = account match
      case c: CheckingAccount => BankException.formatCurrency(c.overdraftLimit)
      case _                  => "None (Savings Account)"

    val txSection =
      if transactions.isEmpty then
        "  (No transactions recorded)"
      else
        transactions
          .map { tx =>
            f"  [${tx.timestamp}%-24s] [${tx.id}%-9s] ${tx.description}"
          }
          .mkString("\n")

    s"""================================================================================
       |                           ACCOUNT STATEMENT
       |================================================================================
       |Account ID      : ${account.id}
       |Account Type    : ${account.accountType}
       |Owner Name      : ${account.ownerName}
       |Status          : ${account.status}
       |Current Balance : ${BankException.formatCurrency(account.balance)}
       |Overdraft Limit : $overdraftStr
       |Statement Date  : $generatedAt
       |--------------------------------------------------------------------------------
       |TRANSACTION HISTORY (${transactions.size} records):
       |--------------------------------------------------------------------------------
       |$txSection
       |================================================================================""".stripMargin

/** Singleton Bank object serving as the single entry point for all banking operations.
  *
  * Features thread-safe atomic state transitions, input validation, custom exception
  * handling, and dual APIs (throwing domain exceptions or returning Either[BankException, A]).
  */
object Bank:

  private val accountsRef = new AtomicReference[Map[String, Account]](Map.empty)
  private val accountIdSeq = new AtomicLong(1000L)
  private val transactionIdSeq = new AtomicLong(5000L)

  @volatile private var bankClock: Clock = Clock.systemUTC()

  /** Sets the clock used for generating transaction timestamps (useful for deterministic tests). */
  def setClock(clock: Clock): Unit =
    bankClock = clock

  /** Generates a unique account identifier. */
  private def generateAccountId(): String =
    s"ACC-${accountIdSeq.incrementAndGet()}"

  /** Generates a unique transaction identifier. */
  private def generateTransactionId(): String =
    s"TXN-${transactionIdSeq.incrementAndGet()}"

  /** Performs an atomic update on the account registry using compare-and-set retry loop. */
  @annotation.tailrec
  private def atomicUpdate[A](
      f: Map[String, Account] => Either[BankException, (Map[String, Account], A)]
  ): Either[BankException, A] =
    val current = accountsRef.get()
    f(current) match
      case Left(err) => Left(err)
      case Right((next, result)) =>
        if accountsRef.compareAndSet(current, next) then Right(result)
        else atomicUpdate(f)

  // ==========================================================================
  // Account Opening
  // ==========================================================================

  /** Safely opens a new Savings Account, returning either a BankException or the created account.
    *
    * @param ownerName owner legal name
    * @param initialDeposit optional initial deposit (must be >= 0.00)
    * @param customId optional explicit ID (primarily for tests)
    */
  def openSavingsAccountSafe(
      ownerName: String,
      initialDeposit: BigDecimal = BigDecimal(0),
      customId: Option[String] = None
  ): Either[BankException, SavingsAccount] =
    for
      validName <- Account.validateOwnerName(ownerName)
      _         <- Account.validateNonNegativeAmount(initialDeposit, "Initial deposit")
      account   <- {
        val now = bankClock.instant()
        val id = customId.getOrElse(generateAccountId())

        val (balance, history) =
          if initialDeposit > BigDecimal(0) then
            val txId = generateTransactionId()
            val tx = Transaction.Deposit(txId, id, initialDeposit, now)
            (initialDeposit, List(tx))
          else
            (BigDecimal(0), Nil)

        val newAccount = SavingsAccount(
          id = id,
          ownerName = validName,
          balance = balance,
          status = AccountStatus.Active,
          transactionHistory = history
        )

        atomicUpdate[SavingsAccount] { registry =>
          if registry.contains(id) then
            Left(DuplicateAccountException(id))
          else
            Right((registry.updated(id, newAccount), newAccount))
        }
      }
    yield account

  /** Opens a new Savings Account, throwing BankException on failure. */
  def openSavingsAccount(
      ownerName: String,
      initialDeposit: BigDecimal = BigDecimal(0)
  ): SavingsAccount =
    openSavingsAccountSafe(ownerName, initialDeposit).fold(throw _, identity)

  /** Opens a new Savings Account with an explicit ID, throwing DuplicateAccountException if already taken. */
  def openSavingsAccountWithId(
      id: String,
      ownerName: String,
      initialDeposit: BigDecimal = BigDecimal(0)
  ): SavingsAccount =
    openSavingsAccountSafe(ownerName, initialDeposit, Some(id)).fold(throw _, identity)

  /** Safely opens a new Checking Account with a configurable overdraft limit.
    *
    * @param ownerName owner legal name
    * @param initialDeposit optional initial deposit (must be >= 0.00)
    * @param overdraftLimit configured overdraft limit (must be >= 0.00)
    * @param customId optional explicit ID (primarily for tests)
    */
  def openCheckingAccountSafe(
      ownerName: String,
      initialDeposit: BigDecimal = BigDecimal(0),
      overdraftLimit: BigDecimal = BigDecimal(0),
      customId: Option[String] = None
  ): Either[BankException, CheckingAccount] =
    for
      validName  <- Account.validateOwnerName(ownerName)
      _          <- Account.validateNonNegativeAmount(initialDeposit, "Initial deposit")
      validLimit <- Account.validateNonNegativeAmount(overdraftLimit, "Overdraft limit")
      account    <- {
        val now = bankClock.instant()
        val id = customId.getOrElse(generateAccountId())

        val (balance, history) =
          if initialDeposit > BigDecimal(0) then
            val txId = generateTransactionId()
            val tx = Transaction.Deposit(txId, id, initialDeposit, now)
            (initialDeposit, List(tx))
          else
            (BigDecimal(0), Nil)

        val newAccount = CheckingAccount(
          id = id,
          ownerName = validName,
          balance = balance,
          overdraftLimit = validLimit,
          status = AccountStatus.Active,
          transactionHistory = history
        )

        atomicUpdate[CheckingAccount] { registry =>
          if registry.contains(id) then
            Left(DuplicateAccountException(id))
          else
            Right((registry.updated(id, newAccount), newAccount))
        }
      }
    yield account

  /** Opens a new Checking Account, throwing BankException on failure. */
  def openCheckingAccount(
      ownerName: String,
      initialDeposit: BigDecimal = BigDecimal(0),
      overdraftLimit: BigDecimal = BigDecimal(0)
  ): CheckingAccount =
    openCheckingAccountSafe(ownerName, initialDeposit, overdraftLimit).fold(throw _, identity)

  /** Opens a new Checking Account with an explicit ID, throwing DuplicateAccountException if already taken. */
  def openCheckingAccountWithId(
      id: String,
      ownerName: String,
      initialDeposit: BigDecimal = BigDecimal(0),
      overdraftLimit: BigDecimal = BigDecimal(0)
  ): CheckingAccount =
    openCheckingAccountSafe(ownerName, initialDeposit, overdraftLimit, Some(id)).fold(throw _, identity)

  // ==========================================================================
  // Account Closing
  // ==========================================================================

  /** Safely closes an account if it exists, is active, and has a zero balance. */
  def closeAccountSafe(accountId: String): Either[BankException, Account] =
    atomicUpdate[Account] { registry =>
      registry.get(accountId) match
        case None =>
          Left(AccountNotFoundException(accountId))
        case Some(acc) =>
          acc.closeSafe.map { closedAcc =>
            (registry.updated(accountId, closedAcc), closedAcc)
          }
    }

  /** Closes an account, throwing BankException on failure. */
  def closeAccount(accountId: String): Account =
    closeAccountSafe(accountId).fold(throw _, identity)

  // ==========================================================================
  // Account Lookup
  // ==========================================================================

  /** Finds an account by ID if present. */
  def findAccount(accountId: String): Option[Account] =
    accountsRef.get().get(accountId)

  /** Safely retrieves an account by ID. */
  def getAccountSafe(accountId: String): Either[AccountNotFoundException, Account] =
    findAccount(accountId).toRight(AccountNotFoundException(accountId))

  /** Retrieves an account by ID, throwing AccountNotFoundException if not found. */
  def getAccount(accountId: String): Account =
    getAccountSafe(accountId).fold(throw _, identity)

  /** Returns all registered accounts sorted by ID. */
  def allAccounts: List[Account] =
    accountsRef.get().values.toList.sortBy(_.id)

  // ==========================================================================
  // Deposit Operation
  // ==========================================================================

  /** Safely deposits funds into an account. */
  def depositSafe(accountId: String, amount: BigDecimal): Either[BankException, Transaction.Deposit] =
    Account.validateAmount(amount).flatMap { validAmount =>
      val txId = generateTransactionId()
      val now = bankClock.instant()
      atomicUpdate[Transaction.Deposit] { registry =>
        registry.get(accountId) match
          case None =>
            Left(AccountNotFoundException(accountId))
          case Some(account) =>
            account.depositSafe(validAmount, txId, now).map { updatedAccount =>
              val tx = Transaction.Deposit(txId, accountId, validAmount, now)
              (registry.updated(accountId, updatedAccount), tx)
            }
      }
    }

  /** Deposits funds into an account, throwing BankException on failure. */
  def deposit(accountId: String, amount: BigDecimal): Transaction.Deposit =
    depositSafe(accountId, amount).fold(throw _, identity)

  // ==========================================================================
  // Withdrawal Operation
  // ==========================================================================

  /** Safely withdraws funds from an account. */
  def withdrawSafe(accountId: String, amount: BigDecimal): Either[BankException, Transaction.Withdrawal] =
    Account.validateAmount(amount).flatMap { validAmount =>
      val txId = generateTransactionId()
      val now = bankClock.instant()
      atomicUpdate[Transaction.Withdrawal] { registry =>
        registry.get(accountId) match
          case None =>
            Left(AccountNotFoundException(accountId))
          case Some(account) =>
            account.withdrawSafe(validAmount, txId, now).map { updatedAccount =>
              val tx = Transaction.Withdrawal(txId, accountId, validAmount, now)
              (registry.updated(accountId, updatedAccount), tx)
            }
      }
    }

  /** Withdraws funds from an account, throwing BankException on failure. */
  def withdraw(accountId: String, amount: BigDecimal): Transaction.Withdrawal =
    withdrawSafe(accountId, amount).fold(throw _, identity)

  // ==========================================================================
  // Atomic Transfer Operation
  // ==========================================================================

  /** Safely transfers funds atomically from sourceAccountId to targetAccountId.
    *
    * Guarantees atomicity: if any step fails (validation, missing account, closed account,
    * insufficient funds), neither account's state is modified.
    */
  def transferSafe(
      sourceAccountId: String,
      targetAccountId: String,
      amount: BigDecimal
  ): Either[BankException, Transaction.Transfer] =
    if sourceAccountId == targetAccountId then
      Left(InvalidOperationException(s"Cannot transfer to the same account: '$sourceAccountId'."))
    else
      Account.validateAmount(amount).flatMap { validAmount =>
        val txId = generateTransactionId()
        val now = bankClock.instant()

        atomicUpdate[Transaction.Transfer] { registry =>
          for
            sourceAccount <- registry.get(sourceAccountId).toRight(AccountNotFoundException(sourceAccountId))
            targetAccount <- registry.get(targetAccountId).toRight(AccountNotFoundException(targetAccountId))
            updatedSource <- sourceAccount.applyTransferOutSafe(targetAccountId, validAmount, txId, now)
            updatedTarget <- targetAccount.applyTransferInSafe(sourceAccountId, validAmount, txId, now)
          yield
            val tx = Transaction.Transfer(txId, sourceAccountId, targetAccountId, validAmount, now)
            val updatedRegistry = registry
              .updated(sourceAccountId, updatedSource)
              .updated(targetAccountId, updatedTarget)
            (updatedRegistry, tx)
        }
      }

  /** Transfers funds between two accounts, throwing BankException on failure. */
  def transfer(
      sourceAccountId: String,
      targetAccountId: String,
      amount: BigDecimal
  ): Transaction.Transfer =
    transferSafe(sourceAccountId, targetAccountId, amount).fold(throw _, identity)

  // ==========================================================================
  // Statements & Transaction History
  // ==========================================================================

  /** Safely retrieves the transaction history for an account. */
  def getTransactionHistorySafe(accountId: String): Either[AccountNotFoundException, List[Transaction]] =
    getAccountSafe(accountId).map(_.transactionHistory)

  /** Retrieves the transaction history for an account, throwing AccountNotFoundException if not found. */
  def getTransactionHistory(accountId: String): List[Transaction] =
    getTransactionHistorySafe(accountId).fold(throw _, identity)

  /** Safely generates a formatted statement for an account. */
  def getStatementSafe(accountId: String): Either[AccountNotFoundException, AccountStatement] =
    getAccountSafe(accountId).map { acc =>
      AccountStatement(acc, bankClock.instant(), acc.transactionHistory)
    }

  /** Generates a formatted statement for an account, throwing AccountNotFoundException if not found. */
  def getStatement(accountId: String): AccountStatement =
    getStatementSafe(accountId).fold(throw _, identity)

  // ==========================================================================
  // Test Lifecycle Support
  // ==========================================================================

  /** Resets the Bank singleton state to a fresh registry (primarily for test isolation). */
  def reset(): Unit =
    accountsRef.set(Map.empty)
    accountIdSeq.set(1000L)
    transactionIdSeq.set(5000L)
    bankClock = Clock.systemUTC()
