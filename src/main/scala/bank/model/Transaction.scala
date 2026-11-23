package bank.model

import java.time.Instant
import bank.exceptions.BankException

/** Sealed hierarchy representing immutable financial transactions. */
sealed trait Transaction:
  /** Unique identifier for the transaction. */
  def id: String

  /** Monetary transaction amount (always strictly positive). */
  def amount: BigDecimal

  /** UTC timestamp when the transaction was executed. */
  def timestamp: Instant

  /** Set of all account identifiers involved in this transaction. */
  def involvedAccountIds: Set[String]

  /** Human-readable description of this transaction. */
  def description: String

object Transaction:

  /** Represents a deposit transaction into an account.
    *
    * @param id unique transaction identifier
    * @param accountId target account identifier
    * @param amount positive deposit amount
    * @param timestamp execution timestamp
    */
  final case class Deposit(
      id: String,
      accountId: String,
      amount: BigDecimal,
      timestamp: Instant
  ) extends Transaction:
    override def involvedAccountIds: Set[String] = Set(accountId)
    override def description: String =
      f"Deposit: +${BankException.formatCurrency(amount)} into account '$accountId'"

  /** Represents a withdrawal transaction from an account.
    *
    * @param id unique transaction identifier
    * @param accountId source account identifier
    * @param amount positive withdrawal amount
    * @param timestamp execution timestamp
    */
  final case class Withdrawal(
      id: String,
      accountId: String,
      amount: BigDecimal,
      timestamp: Instant
  ) extends Transaction:
    override def involvedAccountIds: Set[String] = Set(accountId)
    override def description: String =
      f"Withdrawal: -${BankException.formatCurrency(amount)} from account '$accountId'"

  /** Represents a funds transfer between two distinct accounts.
    *
    * @param id unique transaction identifier
    * @param sourceAccountId origin account identifier
    * @param targetAccountId destination account identifier
    * @param amount positive transfer amount
    * @param timestamp execution timestamp
    */
  final case class Transfer(
      id: String,
      sourceAccountId: String,
      targetAccountId: String,
      amount: BigDecimal,
      timestamp: Instant
  ) extends Transaction:
    override def involvedAccountIds: Set[String] = Set(sourceAccountId, targetAccountId)
    override def description: String =
      f"Transfer: ${BankException.formatCurrency(amount)} from '$sourceAccountId' to '$targetAccountId'"
