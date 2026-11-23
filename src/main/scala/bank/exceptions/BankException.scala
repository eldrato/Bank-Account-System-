package bank.exceptions

/** Base exception for all bank domain failures.
  *
  * @param message human-readable error description
  * @param cause optional underlying exception cause
  */
sealed abstract class BankException(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)

object BankException:
  /** Formats a BigDecimal currency amount as a standard dollar string (e.g. "$1,234.56" or "-$50.00"). */
  def formatCurrency(amount: BigDecimal): String =
    if amount < 0 then
      f"-$$${(-amount)}%,.2f"
    else
      f"$$${amount}%,.2f"

/** Thrown when an account does not have sufficient funds for a withdrawal or transfer.
  *
  * @param accountId identifier of the account
  * @param balance current account balance
  * @param requestedAmount withdrawal or transfer amount requested
  */
final case class InsufficientFundsException(
    accountId: String,
    balance: BigDecimal,
    requestedAmount: BigDecimal
) extends BankException(
      s"Insufficient funds in account '$accountId': current balance is " +
        s"${BankException.formatCurrency(balance)}, but requested amount was " +
        s"${BankException.formatCurrency(requestedAmount)}."
    )

/** Thrown when a checking account withdrawal or transfer exceeds its configured overdraft limit.
  *
  * @param accountId identifier of the checking account
  * @param balance current account balance
  * @param overdraftLimit configured overdraft limit
  * @param requestedAmount withdrawal or transfer amount requested
  */
final case class OverdraftLimitExceededException(
    accountId: String,
    balance: BigDecimal,
    overdraftLimit: BigDecimal,
    requestedAmount: BigDecimal
) extends BankException(
      s"Overdraft limit exceeded for checking account '$accountId': current balance is " +
        s"${BankException.formatCurrency(balance)}, overdraft limit is " +
        s"${BankException.formatCurrency(overdraftLimit)} (maximum available: " +
        s"${BankException.formatCurrency(balance + overdraftLimit)}), but requested amount was " +
        s"${BankException.formatCurrency(requestedAmount)}."
    )

/** Thrown when a monetary amount is zero, negative, or has invalid decimal precision.
  *
  * @param amount the invalid monetary amount
  * @param reason explanation describing why the amount is invalid
  */
final case class InvalidAmountException(
    amount: BigDecimal,
    reason: String
) extends BankException(
      s"Invalid amount ${amount}: $reason."
    )

/** Thrown when an account cannot be located in the bank registry.
  *
  * @param accountId identifier of the missing account
  */
final case class AccountNotFoundException(
    accountId: String
) extends BankException(
      s"Account not found with ID: '$accountId'."
    )

/** Thrown when attempting to register an account with an ID that already exists.
  *
  * @param accountId identifier of the duplicate account
  */
final case class DuplicateAccountException(
    accountId: String
) extends BankException(
      s"Account already exists with ID: '$accountId'."
    )

/** Thrown when an invalid banking operation is requested.
  * Examples include:
  *  - Transferring to the same account
  *  - Operating on a closed account
  *  - Closing an account with a non-zero balance
  *  - Closing an already closed account
  *
  * @param message descriptive explanation of the invalid operation
  */
final case class InvalidOperationException(
    message: String
) extends BankException(message)
