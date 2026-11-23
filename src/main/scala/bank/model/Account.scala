package bank.model

import java.time.Instant
import bank.exceptions.*

/** Represents the operational status of a bank account. */
sealed trait AccountStatus

object AccountStatus:
  /** Account is open and active for normal banking operations. */
  case object Active extends AccountStatus

  /** Account has been closed and can no longer execute transactions. */
  case object Closed extends AccountStatus

/** Abstract domain model for a bank account.
  *
  * Encapsulates state: balance and transaction history can only be modified
  * through validated domain operations, returning updated immutable instances.
  */
sealed abstract class Account:
  /** Unique account identifier. */
  def id: String

  /** Legal name of the account owner. */
  def ownerName: String

  /** Current account balance (always BigDecimal, never Double). */
  def balance: BigDecimal

  /** Current operational status of the account. */
  def status: AccountStatus

  /** Chronological history of all immutable transactions executed on this account. */
  def transactionHistory: List[Transaction]

  /** Name of the concrete account type. */
  def accountType: String

  /** Checks if the account is currently active. */
  def isOpen: Boolean = status == AccountStatus.Active

  /** Checks if the account is closed. */
  def isClosed: Boolean = status == AccountStatus.Closed

  /** Validates and deposits funds into this account, returning an updated account instance.
    *
    * @param amount positive deposit amount with at most 2 decimal places
    * @param txId unique transaction id
    * @param timestamp execution timestamp
    * @return updated Account or Left with BankException
    */
  def depositSafe(amount: BigDecimal, txId: String, timestamp: Instant): Either[BankException, Account]

  /** Validates and deposits funds into this account, throwing a BankException on failure. */
  def deposit(amount: BigDecimal, txId: String, timestamp: Instant): Account =
    depositSafe(amount, txId, timestamp).fold(throw _, identity)

  /** Validates and withdraws funds from this account, returning an updated account instance.
    *
    * @param amount positive withdrawal amount with at most 2 decimal places
    * @param txId unique transaction id
    * @param timestamp execution timestamp
    * @return updated Account or Left with BankException
    */
  def withdrawSafe(amount: BigDecimal, txId: String, timestamp: Instant): Either[BankException, Account]

  /** Validates and withdraws funds from this account, throwing a BankException on failure. */
  def withdraw(amount: BigDecimal, txId: String, timestamp: Instant): Account =
    withdrawSafe(amount, txId, timestamp).fold(throw _, identity)

  /** Validates and debits funds for an outbound transfer.
    *
    * @param targetAccountId recipient account identifier
    * @param amount transfer amount
    * @param txId unique transaction id
    * @param timestamp execution timestamp
    * @return updated Account or Left with BankException
    */
  def applyTransferOutSafe(
      targetAccountId: String,
      amount: BigDecimal,
      txId: String,
      timestamp: Instant
  ): Either[BankException, Account]

  /** Validates and debits funds for an outbound transfer, throwing a BankException on failure. */
  def applyTransferOut(
      targetAccountId: String,
      amount: BigDecimal,
      txId: String,
      timestamp: Instant
  ): Account =
    applyTransferOutSafe(targetAccountId, amount, txId, timestamp).fold(throw _, identity)

  /** Validates and credits funds for an inbound transfer.
    *
    * @param sourceAccountId origin account identifier
    * @param amount transfer amount
    * @param txId unique transaction id
    * @param timestamp execution timestamp
    * @return updated Account or Left with BankException
    */
  def applyTransferInSafe(
      sourceAccountId: String,
      amount: BigDecimal,
      txId: String,
      timestamp: Instant
  ): Either[BankException, Account]

  /** Validates and credits funds for an inbound transfer, throwing a BankException on failure. */
  def applyTransferIn(
      sourceAccountId: String,
      amount: BigDecimal,
      txId: String,
      timestamp: Instant
  ): Account =
    applyTransferInSafe(sourceAccountId, amount, txId, timestamp).fold(throw _, identity)

  /** Validates and closes this account, returning an updated account with status Closed.
    *
    * @return updated Account or Left with BankException
    */
  def closeSafe: Either[BankException, Account]

  /** Validates and closes this account, throwing a BankException on failure. */
  def close: Account =
    closeSafe.fold(throw _, identity)

object Account:

  /** Validates that a monetary amount is strictly positive and does not exceed 2 decimal places. */
  def validateAmount(amount: BigDecimal): Either[InvalidAmountException, BigDecimal] =
    if amount <= BigDecimal(0) then
      Left(InvalidAmountException(amount, "amount must be strictly positive (> 0.00)"))
    else if amount.scale > 2 then
      Left(InvalidAmountException(amount, "amount exceeds maximum supported precision of 2 decimal places"))
    else
      Right(amount)

  /** Validates that a monetary parameter (like overdraft limit) is non-negative with at most 2 decimal places. */
  def validateNonNegativeAmount(amount: BigDecimal, label: String): Either[InvalidAmountException, BigDecimal] =
    if amount < BigDecimal(0) then
      Left(InvalidAmountException(amount, s"$label cannot be negative"))
    else if amount.scale > 2 then
      Left(InvalidAmountException(amount, s"$label exceeds maximum supported precision of 2 decimal places"))
    else
      Right(amount)

  /** Validates that an owner name is non-empty and non-blank. */
  def validateOwnerName(name: String): Either[InvalidOperationException, String] =
    val trimmed = if name == null then "" else name.trim
    if trimmed.isEmpty then
      Left(InvalidOperationException("Account owner name cannot be empty or blank."))
    else
      Right(trimmed)

/** A savings account that does not permit overdrafts. Balance cannot fall below 0.00.
  *
  * @param id unique account identifier
  * @param ownerName owner's legal name
  * @param balance current balance
  * @param status operational status
  * @param transactionHistory immutable transaction log
  */
final case class SavingsAccount(
    id: String,
    ownerName: String,
    balance: BigDecimal = BigDecimal(0),
    status: AccountStatus = AccountStatus.Active,
    transactionHistory: List[Transaction] = Nil
) extends Account:

  override val accountType: String = "Savings"

  private def checkActive: Either[BankException, Unit] =
    if isClosed then Left(InvalidOperationException(s"Cannot perform operation on closed account '$id'."))
    else Right(())

  override def depositSafe(amount: BigDecimal, txId: String, timestamp: Instant): Either[BankException, SavingsAccount] =
    for
      _           <- checkActive
      validAmount <- Account.validateAmount(amount)
    yield
      val tx = Transaction.Deposit(txId, id, validAmount, timestamp)
      copy(
        balance = balance + validAmount,
        transactionHistory = transactionHistory :+ tx
      )

  override def withdrawSafe(amount: BigDecimal, txId: String, timestamp: Instant): Either[BankException, SavingsAccount] =
    for
      _           <- checkActive
      validAmount <- Account.validateAmount(amount)
      _           <- Either.cond(validAmount <= balance, (), InsufficientFundsException(id, balance, validAmount))
    yield
      val tx = Transaction.Withdrawal(txId, id, validAmount, timestamp)
      copy(
        balance = balance - validAmount,
        transactionHistory = transactionHistory :+ tx
      )

  override def applyTransferOutSafe(
      targetAccountId: String,
      amount: BigDecimal,
      txId: String,
      timestamp: Instant
  ): Either[BankException, SavingsAccount] =
    for
      _           <- checkActive
      validAmount <- Account.validateAmount(amount)
      _           <- Either.cond(validAmount <= balance, (), InsufficientFundsException(id, balance, validAmount))
    yield
      val tx = Transaction.Transfer(txId, id, targetAccountId, validAmount, timestamp)
      copy(
        balance = balance - validAmount,
        transactionHistory = transactionHistory :+ tx
      )

  override def applyTransferInSafe(
      sourceAccountId: String,
      amount: BigDecimal,
      txId: String,
      timestamp: Instant
  ): Either[BankException, SavingsAccount] =
    for
      _           <- checkActive
      validAmount <- Account.validateAmount(amount)
    yield
      val tx = Transaction.Transfer(txId, sourceAccountId, id, validAmount, timestamp)
      copy(
        balance = balance + validAmount,
        transactionHistory = transactionHistory :+ tx
      )

  override def closeSafe: Either[BankException, SavingsAccount] =
    if isClosed then
      Left(InvalidOperationException(s"Account '$id' is already closed."))
    else if balance != BigDecimal(0) then
      Left(InvalidOperationException(
        s"Cannot close account '$id': non-zero balance of ${BankException.formatCurrency(balance)} (must be zero)."
      ))
    else
      Right(copy(status = AccountStatus.Closed))

/** A checking account that permits overdraft up to a configurable limit.
  *
  * @param id unique account identifier
  * @param ownerName owner's legal name
  * @param balance current balance (may be negative down to -overdraftLimit)
  * @param overdraftLimit maximum allowed negative balance (>= 0.00)
  * @param status operational status
  * @param transactionHistory immutable transaction log
  */
final case class CheckingAccount(
    id: String,
    ownerName: String,
    balance: BigDecimal = BigDecimal(0),
    overdraftLimit: BigDecimal = BigDecimal(0),
    status: AccountStatus = AccountStatus.Active,
    transactionHistory: List[Transaction] = Nil
) extends Account:

  override val accountType: String = "Checking"

  private def checkActive: Either[BankException, Unit] =
    if isClosed then Left(InvalidOperationException(s"Cannot perform operation on closed account '$id'."))
    else Right(())

  override def depositSafe(amount: BigDecimal, txId: String, timestamp: Instant): Either[BankException, CheckingAccount] =
    for
      _           <- checkActive
      validAmount <- Account.validateAmount(amount)
    yield
      val tx = Transaction.Deposit(txId, id, validAmount, timestamp)
      copy(
        balance = balance + validAmount,
        transactionHistory = transactionHistory :+ tx
      )

  override def withdrawSafe(amount: BigDecimal, txId: String, timestamp: Instant): Either[BankException, CheckingAccount] =
    for
      _           <- checkActive
      validAmount <- Account.validateAmount(amount)
      available    = balance + overdraftLimit
      _           <- Either.cond(validAmount <= available, (), OverdraftLimitExceededException(id, balance, overdraftLimit, validAmount))
    yield
      val tx = Transaction.Withdrawal(txId, id, validAmount, timestamp)
      copy(
        balance = balance - validAmount,
        transactionHistory = transactionHistory :+ tx
      )

  override def applyTransferOutSafe(
      targetAccountId: String,
      amount: BigDecimal,
      txId: String,
      timestamp: Instant
  ): Either[BankException, CheckingAccount] =
    for
      _           <- checkActive
      validAmount <- Account.validateAmount(amount)
      available    = balance + overdraftLimit
      _           <- Either.cond(validAmount <= available, (), OverdraftLimitExceededException(id, balance, overdraftLimit, validAmount))
    yield
      val tx = Transaction.Transfer(txId, id, targetAccountId, validAmount, timestamp)
      copy(
        balance = balance - validAmount,
        transactionHistory = transactionHistory :+ tx
      )

  override def applyTransferInSafe(
      sourceAccountId: String,
      amount: BigDecimal,
      txId: String,
      timestamp: Instant
  ): Either[BankException, CheckingAccount] =
    for
      _           <- checkActive
      validAmount <- Account.validateAmount(amount)
    yield
      val tx = Transaction.Transfer(txId, sourceAccountId, id, validAmount, timestamp)
      copy(
        balance = balance + validAmount,
        transactionHistory = transactionHistory :+ tx
      )

  override def closeSafe: Either[BankException, CheckingAccount] =
    if isClosed then
      Left(InvalidOperationException(s"Account '$id' is already closed."))
    else if balance != BigDecimal(0) then
      Left(InvalidOperationException(
        s"Cannot close account '$id': non-zero balance of ${BankException.formatCurrency(balance)} (must be zero)."
      ))
    else
      Right(copy(status = AccountStatus.Closed))
