package bank.server

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.{InetSocketAddress, URLDecoder}
import java.nio.charset.StandardCharsets
import scala.util.Try
import bank.Bank
import bank.exceptions.*
import bank.model.*

/** Lightweight, zero-dependency HTTP server providing a web UI and REST API on localhost.
  *
  * Uses the built-in JDK HttpServer (part of the standard library runtime) and renders
  * responsive HTML/CSS directly in Scala.
  */
class BankWebServer(val port: Int = 8080):

  private var serverOpt: Option[HttpServer] = None

  def start(): Unit =
    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/", new RootHandler())
    server.createContext("/statement", new StatementHandler())
    server.createContext("/accounts/open", new OpenAccountHandler())
    server.createContext("/deposit", new DepositHandler())
    server.createContext("/withdraw", new WithdrawHandler())
    server.createContext("/transfer", new TransferHandler())
    server.createContext("/accounts/close", new CloseAccountHandler())
    server.createContext("/seed", new SeedHandler())
    server.createContext("/reset", new ResetHandler())
    server.createContext("/api/accounts", new ApiAccountsHandler())
    server.createContext("/api/health", new HealthHandler())
    server.setExecutor(null) // default implementation
    server.start()
    serverOpt = Some(server)

  def stop(): Unit =
    serverOpt.foreach(_.stop(0))

object BankWebServer:

  /** Helper to seed demo data. */
  def seedDemoData(): Unit =
    Bank.reset()
    val alice = Bank.openSavingsAccount("Alice Smith", BigDecimal("500.00"))
    val bob = Bank.openCheckingAccount("Bob Jones", BigDecimal("200.00"), BigDecimal("300.00"))
    val charlie = Bank.openCheckingAccount("Charlie Brown", BigDecimal("0.00"), BigDecimal("100.00"))

    Bank.deposit(alice.id, BigDecimal("250.00"))
    Bank.withdraw(alice.id, BigDecimal("100.00"))
    Bank.deposit(bob.id, BigDecimal("50.00"))
    Bank.transfer(alice.id, bob.id, BigDecimal("200.00"))

  /** Starts the web server with seeded demo data. */
  def runServer(port: Int = 8080): BankWebServer =
    seedDemoData()
    val server = new BankWebServer(port)
    server.start()
    println(
      s"""================================================================================
         |              BANK ACCOUNT SYSTEM - LOCALHOST SERVER ACTIVE
         |================================================================================
         |  HTTP Server running at: http://localhost:$port
         |  Dashboard UI          : http://localhost:$port/
         |  REST API              : http://localhost:$port/api/accounts
         |  Health Check          : http://localhost:$port/api/health
         |
         |  Sample accounts for Alice Smith (Savings), Bob Jones (Checking), and
         |  Charlie Brown (Checking) have been seeded and are ready for interaction.
         |================================================================================""".stripMargin
    )
    server

  /** Parses application/x-www-form-urlencoded request bodies. */
  private[server] def parseFormData(body: String): Map[String, String] =
    if body.trim.isEmpty then Map.empty
    else
      body
        .split("&")
        .flatMap { pair =>
          val idx = pair.indexOf("=")
          if idx > 0 then
            val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
            Some(key -> value)
          else None
        }
        .toMap

  /** Parses URL query parameters. */
  private[server] def parseQueryParams(query: String): Map[String, String] =
    if query == null || query.trim.isEmpty then Map.empty
    else parseFormData(query)

  /** Sends an HTML response with UTF-8 encoding. */
  private[server] def sendHtml(exchange: HttpExchange, statusCode: Int, html: String): Unit =
    val bytes = html.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "text/html; charset=UTF-8")
    exchange.sendResponseHeaders(statusCode, bytes.length)
    val os = exchange.getResponseBody
    os.write(bytes)
    os.close()

  /** Sends a JSON response with UTF-8 encoding. */
  private[server] def sendJson(exchange: HttpExchange, statusCode: Int, json: String): Unit =
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json; charset=UTF-8")
    exchange.sendResponseHeaders(statusCode, bytes.length)
    val os = exchange.getResponseBody
    os.write(bytes)
    os.close()

  /** Redirects to a new location (HTTP 303 See Other). */
  private[server] def redirect(exchange: HttpExchange, location: String): Unit =
    exchange.getResponseHeaders.set("Location", location)
    exchange.sendResponseHeaders(303, -1)
    exchange.close()

  /** Reads the entire body of an HTTP request. */
  private[server] def readBody(exchange: HttpExchange): String =
    val is = exchange.getRequestBody
    val bytes = is.readAllBytes()
    new String(bytes, StandardCharsets.UTF_8)

  /** URL encodes a string safely. */
  private[server] def urlEncode(s: String): String =
    java.net.URLEncoder.encode(s, StandardCharsets.UTF_8)

  /** Base HTML template with styling. */
  private[server] def layout(title: String, content: String): String =
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>$title - Bank Account System</title>
       |  <style>
       |    :root {
       |      --bg: #0f172a;
       |      --card-bg: #1e293b;
       |      --border: #334155;
       |      --text: #f8fafc;
       |      --text-muted: #94a3b8;
       |      --primary: #38bdf8;
       |      --primary-hover: #0284c7;
       |      --success: #22c55e;
       |      --danger: #ef4444;
       |      --warning: #f59e0b;
       |      --badge-savings: #06b6d4;
       |      --badge-checking: #8b5cf6;
       |    }
       |    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
       |    body { background-color: var(--bg); color: var(--text); padding: 24px; line-height: 1.5; }
       |    .container { max-width: 1200px; margin: 0 auto; }
       |    header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 24px; border-bottom: 1px solid var(--border); margin-bottom: 24px; }
       |    .brand { font-size: 24px; font-weight: 700; color: var(--primary); display: flex; align-items: center; gap: 10px; }
       |    .brand-sub { font-size: 14px; color: var(--text-muted); font-weight: 400; }
       |    .actions-bar { display: flex; gap: 12px; }
       |    .btn { display: inline-flex; align-items: center; justify-content: center; padding: 8px 16px; border-radius: 6px; font-weight: 600; font-size: 14px; text-decoration: none; cursor: pointer; border: none; transition: background 0.15s ease; }
       |    .btn-primary { background: var(--primary); color: #0f172a; }
       |    .btn-primary:hover { background: var(--primary-hover); }
       |    .btn-secondary { background: var(--card-bg); color: var(--text); border: 1px solid var(--border); }
       |    .btn-secondary:hover { background: #334155; }
       |    .btn-danger { background: var(--danger); color: white; }
       |    .btn-danger:hover { background: #dc2626; }
       |    .btn-sm { padding: 4px 10px; font-size: 12px; }
       |    
       |    .alert { padding: 14px 18px; border-radius: 8px; margin-bottom: 24px; font-size: 14px; display: flex; justify-content: space-between; align-items: center; }
       |    .alert-success { background: rgba(34, 197, 94, 0.15); border: 1px solid var(--success); color: #86efac; }
       |    .alert-danger { background: rgba(239, 68, 68, 0.15); border: 1px solid var(--danger); color: #fca5a5; }
       |    
       |    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; margin-bottom: 28px; }
       |    .stat-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 8px; padding: 18px; }
       |    .stat-title { font-size: 13px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); margin-bottom: 6px; }
       |    .stat-value { font-size: 26px; font-weight: 700; color: var(--text); }
       |    
       |    .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin-bottom: 28px; }
       |    .card-title { font-size: 18px; font-weight: 600; margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center; }
       |    
       |    table { width: 100%; border-collapse: collapse; text-align: left; }
       |    th { font-size: 12px; text-transform: uppercase; color: var(--text-muted); padding: 12px; border-bottom: 1px solid var(--border); }
       |    td { padding: 14px 12px; border-bottom: 1px solid var(--border); font-size: 14px; }
       |    tr:last-child td { border-bottom: none; }
       |    tr:hover td { background: rgba(255, 255, 255, 0.02); }
       |    
       |    .badge { display: inline-block; padding: 3px 8px; border-radius: 12px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
       |    .badge-savings { background: rgba(6, 182, 212, 0.2); color: #38bdf8; border: 1px solid var(--badge-savings); }
       |    .badge-checking { background: rgba(139, 92, 246, 0.2); color: #c084fc; border: 1px solid var(--badge-checking); }
       |    .badge-active { background: rgba(34, 197, 94, 0.2); color: #4ade80; border: 1px solid var(--success); }
       |    .badge-closed { background: rgba(148, 163, 184, 0.2); color: #cbd5e1; border: 1px solid var(--border); }
       |    
       |    .money-pos { color: #4ade80; font-weight: 600; }
       |    .money-neg { color: #f87171; font-weight: 600; }
       |    
       |    .forms-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; }
       |    .form-group { margin-bottom: 14px; }
       |    label { display: block; font-size: 13px; font-weight: 500; color: var(--text-muted); margin-bottom: 6px; }
       |    input, select { width: 100%; padding: 10px 12px; border-radius: 6px; background: #0f172a; border: 1px solid var(--border); color: var(--text); font-size: 14px; outline: none; }
       |    input:focus, select:focus { border-color: var(--primary); }
       |    
       |    .statement-box { background: #090d16; border: 1px solid var(--border); border-radius: 6px; padding: 20px; font-family: monospace; white-space: pre-wrap; font-size: 13px; line-height: 1.6; color: #38bdf8; overflow-x: auto; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <header>
       |      <div>
       |        <div class="brand">🏦 Bank Account System <span class="badge badge-savings" style="font-size:10px;">Scala 3</span></div>
       |        <div class="brand-sub">Functional Domain Engine &bull; Atomic State Transfers &bull; Zero Double Precision</div>
       |      </div>
       |      <div class="actions-bar">
       |        <form action="/seed" method="POST" style="display:inline;">
       |          <button type="submit" class="btn btn-secondary">⚡ Seed Demo Accounts</button>
       |        </form>
       |        <form action="/reset" method="POST" style="display:inline;" onsubmit="return confirm('Reset all accounts to empty?');">
       |          <button type="submit" class="btn btn-danger">↺ Reset Bank</button>
       |        </form>
       |      </div>
       |    </header>
       |    $content
       |  </div>
       |</body>
       |</html>""".stripMargin

// ============================================================================
// Handlers
// ============================================================================

class RootHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    val queryParams = BankWebServer.parseQueryParams(exchange.getRequestURI.getQuery)
    val successMsg = queryParams.get("success")
    val errorMsg = queryParams.get("error")

    val accounts = Bank.allAccounts
    val totalBalance = accounts.map(_.balance).foldLeft(BigDecimal(0))(_ + _)
    val activeCount = accounts.count(_.isOpen)

    val alertSection =
      successMsg.map(msg => s"""<div class="alert alert-success"><span>✓ $msg</span><a href="/" style="color:inherit; text-decoration:none;">&times;</a></div>""").getOrElse("") +
      errorMsg.map(err => s"""<div class="alert alert-danger"><span>⚠ $err</span><a href="/" style="color:inherit; text-decoration:none;">&times;</a></div>""").getOrElse("")

    val accountsRows =
      if accounts.isEmpty then
        """<tr><td colspan="7" style="text-align:center; color:var(--text-muted); padding:30px;">No accounts registered. Click 'Seed Demo Accounts' above or create one below.</td></tr>"""
      else
        accounts
          .map { acc =>
            val typeBadge = if acc.accountType == "Savings" then "badge-savings" else "badge-checking"
            val statusBadge = if acc.isOpen then "badge-active" else "badge-closed"
            val balanceClass = if acc.balance >= 0 then "money-pos" else "money-neg"
            val overdraftVal = acc match
              case c: CheckingAccount => BankException.formatCurrency(c.overdraftLimit)
              case _                  => "None"

            s"""<tr>
               |  <td><strong>${acc.id}</strong></td>
               |  <td>${acc.ownerName}</td>
               |  <td><span class="badge $typeBadge">${acc.accountType}</span></td>
               |  <td class="$balanceClass">${BankException.formatCurrency(acc.balance)}</td>
               |  <td>$overdraftVal</td>
               |  <td><span class="badge $statusBadge">${acc.status}</span></td>
               |  <td>
               |    <a href="/statement?id=${acc.id}" class="btn btn-secondary btn-sm">Statement</a>
               |  </td>
               |</tr>""".stripMargin
          }
          .mkString("\n")

    val accountOptions = accounts.filter(_.isOpen).map { acc =>
      s"""<option value="${acc.id}">${acc.id} - ${acc.ownerName} (${BankException.formatCurrency(acc.balance)})</option>"""
    }.mkString("\n")

    val content =
      s"""$alertSection
         |
         |<div class="stats-grid">
         |  <div class="stat-card">
         |    <div class="stat-title">Total Registered Accounts</div>
         |    <div class="stat-value">${accounts.size}</div>
         |  </div>
         |  <div class="stat-card">
         |    <div class="stat-title">Active Accounts</div>
         |    <div class="stat-value">$activeCount</div>
         |  </div>
         |  <div class="stat-card">
         |    <div class="stat-title">Total Bank Assets</div>
         |    <div class="stat-value money-pos">${BankException.formatCurrency(totalBalance)}</div>
         |  </div>
         |</div>
         |
         |<div class="card">
         |  <div class="card-title">Accounts Ledger</div>
         |  <table>
         |    <thead>
         |      <tr>
         |        <th>Account ID</th>
         |        <th>Owner</th>
         |        <th>Type</th>
         |        <th>Balance</th>
         |        <th>Overdraft Limit</th>
         |        <th>Status</th>
         |        <th>Actions</th>
         |      </tr>
         |    </thead>
         |    <tbody>
         |      $accountsRows
         |    </tbody>
         |  </table>
         |</div>
         |
         |<div class="card">
         |  <div class="card-title">Banking Operations</div>
         |  <div class="forms-grid">
         |    
         |    <!-- Open Account Form -->
         |    <div class="stat-card">
         |      <h3 style="font-size:16px; margin-bottom:12px;">Open New Account</h3>
         |      <form action="/accounts/open" method="POST">
         |        <div class="form-group">
         |          <label>Owner Name</label>
         |          <input type="text" name="ownerName" placeholder="e.g. John Doe" required>
         |        </div>
         |        <div class="form-group">
         |          <label>Account Type</label>
         |          <select name="accountType" id="accountTypeSelect" onchange="document.getElementById('overdraftGroup').style.display = this.value === 'Checking' ? 'block' : 'none';">
         |            <option value="Savings">Savings Account (No Overdraft)</option>
         |            <option value="Checking">Checking Account (With Overdraft)</option>
         |          </select>
         |        </div>
         |        <div class="form-group">
         |          <label>Initial Deposit ($$)</label>
         |          <input type="number" step="0.01" min="0" name="initialDeposit" value="0.00">
         |        </div>
         |        <div class="form-group" id="overdraftGroup" style="display:none;">
         |          <label>Overdraft Limit ($$)</label>
         |          <input type="number" step="0.01" min="0" name="overdraftLimit" value="100.00">
         |        </div>
         |        <button type="submit" class="btn btn-primary" style="width:100%;">Create Account</button>
         |      </form>
         |    </div>
         |
         |    <!-- Deposit Form -->
         |    <div class="stat-card">
         |      <h3 style="font-size:16px; margin-bottom:12px;">Deposit Funds</h3>
         |      <form action="/deposit" method="POST">
         |        <div class="form-group">
         |          <label>Target Account</label>
         |          <select name="accountId" required>
         |            <option value="">Select Account</option>
         |            $accountOptions
         |          </select>
         |        </div>
         |        <div class="form-group">
         |          <label>Amount ($$)</label>
         |          <input type="number" step="0.01" name="amount" placeholder="0.00" required>
         |        </div>
         |        <button type="submit" class="btn btn-primary" style="width:100%;">Deposit Funds</button>
         |      </form>
         |    </div>
         |
         |    <!-- Withdraw Form -->
         |    <div class="stat-card">
         |      <h3 style="font-size:16px; margin-bottom:12px;">Withdraw Funds</h3>
         |      <form action="/withdraw" method="POST">
         |        <div class="form-group">
         |          <label>Source Account</label>
         |          <select name="accountId" required>
         |            <option value="">Select Account</option>
         |            $accountOptions
         |          </select>
         |        </div>
         |        <div class="form-group">
         |          <label>Amount ($$)</label>
         |          <input type="number" step="0.01" name="amount" placeholder="0.00" required>
         |        </div>
         |        <button type="submit" class="btn btn-primary" style="width:100%;">Withdraw Funds</button>
         |      </form>
         |    </div>
         |
         |    <!-- Transfer Form -->
         |    <div class="stat-card">
         |      <h3 style="font-size:16px; margin-bottom:12px;">Atomic Transfer</h3>
         |      <form action="/transfer" method="POST">
         |        <div class="form-group">
         |          <label>From Account</label>
         |          <select name="sourceAccountId" required>
         |            <option value="">Select Source</option>
         |            $accountOptions
         |          </select>
         |        </div>
         |        <div class="form-group">
         |          <label>To Account</label>
         |          <select name="targetAccountId" required>
         |            <option value="">Select Destination</option>
         |            $accountOptions
         |          </select>
         |        </div>
         |        <div class="form-group">
         |          <label>Amount ($$)</label>
         |          <input type="number" step="0.01" name="amount" placeholder="0.00" required>
         |        </div>
         |        <button type="submit" class="btn btn-primary" style="width:100%;">Transfer Funds</button>
         |      </form>
         |    </div>
         |
         |    <!-- Close Account Form -->
         |    <div class="stat-card">
         |      <h3 style="font-size:16px; margin-bottom:12px;">Close Account</h3>
         |      <form action="/accounts/close" method="POST" onsubmit="return confirm('Are you sure you want to close this account? Balance must be $$0.00.');">
         |        <div class="form-group">
         |          <label>Account to Close</label>
         |          <select name="accountId" required>
         |            <option value="">Select Account</option>
         |            $accountOptions
         |          </select>
         |        </div>
         |        <p style="font-size:12px; color:var(--text-muted); margin-bottom:12px;">Notice: Balance must be exactly $$0.00 to close.</p>
         |        <button type="submit" class="btn btn-danger" style="width:100%;">Close Account</button>
         |      </form>
         |    </div>
         |
         |  </div>
         |</div>""".stripMargin

    BankWebServer.sendHtml(exchange, 200, BankWebServer.layout("Dashboard", content))

class StatementHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    val queryParams = BankWebServer.parseQueryParams(exchange.getRequestURI.getQuery)
    val accountId = queryParams.getOrElse("id", "")

    Bank.getStatementSafe(accountId) match
      case Left(err) =>
        BankWebServer.redirect(exchange, s"/?error=${BankWebServer.urlEncode(err.getMessage)}")
      case Right(statement) =>
        val content =
          s"""<div style="margin-bottom: 20px;">
             |  <a href="/" class="btn btn-secondary">&larr; Back to Dashboard</a>
             |</div>
             |<div class="card">
             |  <div class="card-title">Statement for Account ${statement.account.id} (${statement.account.ownerName})</div>
             |  <div class="statement-box">${statement.formatted}</div>
             |</div>""".stripMargin
        BankWebServer.sendHtml(exchange, 200, BankWebServer.layout(s"Statement ${statement.account.id}", content))

class OpenAccountHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    if exchange.getRequestMethod.equalsIgnoreCase("POST") then
      val form = BankWebServer.parseFormData(BankWebServer.readBody(exchange))
      val ownerName = form.getOrElse("ownerName", "")
      val accountType = form.getOrElse("accountType", "Savings")
      val initialDeposit = Try(BigDecimal(form.getOrElse("initialDeposit", "0.00"))).getOrElse(BigDecimal(-1))
      val overdraftLimit = Try(BigDecimal(form.getOrElse("overdraftLimit", "0.00"))).getOrElse(BigDecimal(-1))

      val result = if accountType == "Checking" then
        Bank.openCheckingAccountSafe(ownerName, initialDeposit, overdraftLimit)
      else
        Bank.openSavingsAccountSafe(ownerName, initialDeposit)

      result match
        case Left(err) =>
          BankWebServer.redirect(exchange, s"/?error=${BankWebServer.urlEncode(err.getMessage)}")
        case Right(acc) =>
          BankWebServer.redirect(exchange, s"/?success=${BankWebServer.urlEncode(s"Opened ${acc.accountType} account ${acc.id} for '${acc.ownerName}' with balance ${BankException.formatCurrency(acc.balance)}")}")
    else
      BankWebServer.redirect(exchange, "/")

class DepositHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    if exchange.getRequestMethod.equalsIgnoreCase("POST") then
      val form = BankWebServer.parseFormData(BankWebServer.readBody(exchange))
      val accountId = form.getOrElse("accountId", "")
      val amount = Try(BigDecimal(form.getOrElse("amount", "0.00"))).getOrElse(BigDecimal(-1))

      Bank.depositSafe(accountId, amount) match
        case Left(err) =>
          BankWebServer.redirect(exchange, s"/?error=${BankWebServer.urlEncode(err.getMessage)}")
        case Right(tx) =>
          BankWebServer.redirect(exchange, s"/?success=${BankWebServer.urlEncode(s"Successfully deposited ${BankException.formatCurrency(tx.amount)} into account $accountId")}")
    else
      BankWebServer.redirect(exchange, "/")

class WithdrawHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    if exchange.getRequestMethod.equalsIgnoreCase("POST") then
      val form = BankWebServer.parseFormData(BankWebServer.readBody(exchange))
      val accountId = form.getOrElse("accountId", "")
      val amount = Try(BigDecimal(form.getOrElse("amount", "0.00"))).getOrElse(BigDecimal(-1))

      Bank.withdrawSafe(accountId, amount) match
        case Left(err) =>
          BankWebServer.redirect(exchange, s"/?error=${BankWebServer.urlEncode(err.getMessage)}")
        case Right(tx) =>
          BankWebServer.redirect(exchange, s"/?success=${BankWebServer.urlEncode(s"Successfully withdrew ${BankException.formatCurrency(tx.amount)} from account $accountId")}")
    else
      BankWebServer.redirect(exchange, "/")

class TransferHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    if exchange.getRequestMethod.equalsIgnoreCase("POST") then
      val form = BankWebServer.parseFormData(BankWebServer.readBody(exchange))
      val sourceId = form.getOrElse("sourceAccountId", "")
      val targetId = form.getOrElse("targetAccountId", "")
      val amount = Try(BigDecimal(form.getOrElse("amount", "0.00"))).getOrElse(BigDecimal(-1))

      Bank.transferSafe(sourceId, targetId, amount) match
        case Left(err) =>
          BankWebServer.redirect(exchange, s"/?error=${BankWebServer.urlEncode(err.getMessage)}")
        case Right(tx) =>
          BankWebServer.redirect(exchange, s"/?success=${BankWebServer.urlEncode(s"Successfully transferred ${BankException.formatCurrency(tx.amount)} from $sourceId to $targetId")}")
    else
      BankWebServer.redirect(exchange, "/")

class CloseAccountHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    if exchange.getRequestMethod.equalsIgnoreCase("POST") then
      val form = BankWebServer.parseFormData(BankWebServer.readBody(exchange))
      val accountId = form.getOrElse("accountId", "")

      Bank.closeAccountSafe(accountId) match
        case Left(err) =>
          BankWebServer.redirect(exchange, s"/?error=${BankWebServer.urlEncode(err.getMessage)}")
        case Right(_) =>
          BankWebServer.redirect(exchange, s"/?success=${BankWebServer.urlEncode(s"Account $accountId has been closed successfully.")}")
    else
      BankWebServer.redirect(exchange, "/")

class SeedHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    if exchange.getRequestMethod.equalsIgnoreCase("POST") then
      BankWebServer.seedDemoData()
      BankWebServer.redirect(exchange, "/?success=" + BankWebServer.urlEncode("Demo accounts for Alice, Bob, and Charlie have been seeded!"))
    else
      BankWebServer.redirect(exchange, "/")

class ResetHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    if exchange.getRequestMethod.equalsIgnoreCase("POST") then
      Bank.reset()
      BankWebServer.redirect(exchange, "/?success=" + BankWebServer.urlEncode("Bank registry reset to clean state."))
    else
      BankWebServer.redirect(exchange, "/")

class ApiAccountsHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    val accounts = Bank.allAccounts
    val jsonItems = accounts.map { acc =>
      val overdraft = acc match
        case c: CheckingAccount => s""""overdraftLimit": ${c.overdraftLimit},"""
        case _                  => ""
      s"""{"id":"${acc.id}","ownerName":"${acc.ownerName}","accountType":"${acc.accountType}","balance":${acc.balance},${overdraft}"status":"${acc.status}"}"""
    }
    val json = s"""[${jsonItems.mkString(",")}]"""
    BankWebServer.sendJson(exchange, 200, json)

class HealthHandler extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    BankWebServer.sendJson(exchange, 200, """{"status":"UP","system":"Bank Account System"}""")
