# Features

This page documents the storage, session, brute-force protection, and admin
tooling added on top of the base login/register flow. All settings below live
in `config.yml`; run `/openlogin reload` after editing it.

## Storage backends

`Storage.type` selects where accounts are stored: `SQLITE` (default, zero
setup) or `MYSQL`.

```yaml
Storage:
  type: 'SQLITE'

  # Only used when type is set to MYSQL.
  MySQL:
    host: 'localhost'
    port: 3306
    database: 'openlogin'
    username: 'root'
    password: ''
    useSSL: false
    pool-size: 10
```

MySQL/MariaDB connections are pooled (via HikariCP), so `pool-size` caps how
many concurrent connections the plugin may open. Switching `type` does not
migrate existing data between backends — it only changes where new reads and
writes go.

## Remember-me sessions

`Security.session.timeout-minutes` lets a returning player skip re-entering
their password if they rejoin from the same IP address within the configured
window. It's disabled by default.

```yaml
Security:
  session:
    # Minutes a returning player may skip re-entering their password when
    # rejoining from the same IP address they last logged in from.
    # Set to 0 to disable.
    timeout-minutes: 0
```

A session is valid when both are true:
- the joining IP matches the address stored on the account from its last
  successful login (manual or auto), and
- the time since that last login is within `timeout-minutes`.

Every successful login (manual or session-based) refreshes the stored address
and timestamp, so the window effectively resets on each join.

## Brute-force protection

```yaml
Security:
  brute-force:
    # How many wrong passwords a player may enter before being locked out.
    # Set to 0 to disable this protection.
    max-login-attempts: 5

    # How many minutes a lockout lasts (and the window that failed attempts
    # are counted in).
    reset-minutes: 10

    # Maximum number of accounts that may be registered from the same IP
    # address. Set to 0 to disable this limit.
    max-accounts-per-ip: 3
```

- **Login attempts**: since any wrong password already disconnects the
  player, the counter is tracked by account name across reconnects (not
  reset on disconnect) so a script can't just rejoin to reset it. Once the
  cap is hit within the `reset-minutes` window, every join attempt is kicked
  immediately — correct password or not — until the window expires.
- **Accounts per IP**: checked at `/register` time; once an IP hits the
  limit, further registrations from it are refused until an existing account
  from that IP is removed.

## Admin commands

Everything below requires the `openlogin.admin` permission (`op` by
default), and works for in-game admins as well as console — previously
force-actions on other players were console-only.

| Command | Description |
|---|---|
| `/openlogin admin sessions` | Lists online players who haven't authenticated yet. |
| `/openlogin admin forcelogin <player>` | Authenticates an online player without a password. |
| `/openlogin admin unregister <player>` | Deletes a player's account and kicks them if online. |
| `/openlogin admin changepassword <player> <newpassword>` | Sets a player's password without needing their current one. |

`/openlogin reload`, `/openlogin update`, and console-run
`/register <player> <password>`, `/changepassword <player> <newpassword>`,
and `/unregister <player>` are unchanged and documented by their in-game
usage messages.
