# Protokin (Telegram Bot)

## Start Project
1. Install Clojure CLI.
2. Prepare `config.edn`:

```clojure
{:telegram-bot-token "YOUR_BOT_TOKEN"
 :postgres           {:dbtype   "postgres"
                      :host     "localhost"
                      :dbname   "mydb"
                      :username "postgres"
                      :password "mydb"}}
```

3. Run migrations manually:

```bash
clj -M:dev -e "(require 'protokin.system.migrations) (protokin.system.migrations/migrate!)"
```

4. Start app:

```bash
clj -M -m protokin.core
```

or run with env-file script:

```bash
cp .env.local.example .env.local
./scripts/run-with-env.sh
```

Health check:

```bash
curl http://localhost:8080/health
# {"status":"ok"}
```

## Development
Start dev REPL:

```bash
clj -M:dev
```

In REPL:

```clojure
(user/start)
(user/stop)
(user/reset)
(user/migrate)
(user/rollback)
```

Notes:
- `user/reset` reloads changed namespaces and restarts components.
- `CONFIG_PATH` can point to another config file.
- Keep production secrets out of git-tracked files.

## Telegram Flow
This app uses long polling (not webhook).

1. Bot loop calls Telegram `getUpdates` over HTTPS.
2. Each update is claimed in `tg_updates` to prevent duplicate processing.
3. Command entrypoint is `src/protokin/telegram/handlers.clj` (`handle-update`).
4. Route table is composed from `src/protokin/telegram/methods/*`.

Connection model:
- Telegram does not call your local machine directly in polling mode.
- Your local app pulls updates from Telegram.
- So polling works on localhost without public HTTPS endpoint.

## Privacy + Encryption Flow
Expense content is encrypted at application level.

- Encrypted payload fields: amount, category, transaction name, notes.
- Plaintext metadata: `user_id`, `expense_date`, `short_id`, timestamps.
- Per-user encryption key (DEK) is wrapped by passphrase-derived key (KEK).
- Passphrase is never stored.
- `/unlock` stores DEK in memory for 15 minutes session TTL.

New security commands:
- `/set-passphrase <passphrase>`
- `/unlock <passphrase>`
- `/lock`
- `/change-passphrase <old> <new>`
- `/delete-account CONFIRM`

## Expense Commands (V1)
Built-in categories are in `src/protokin/utils/variables.clj`.

- `/categories`
- `/add <amount> <category> <transaction name>`
- `/add <amount> <category> <transaction name>; note=<text>; date=<YYYY-MM-DD>`
- `/review`
- `/review YYYY-MM`
- `/review YYYY-MM-DD`
- `/review-csv YYYY-MM`
- `/review-chart` (placeholder)
- `/delete <short_id>`

Examples:
- `/start`
- `/set-passphrase my-strong-passphrase`
- `/unlock my-strong-passphrase`
- `/add 15000 food lunch`
- `/review 2026-03`
- `/review 2026-03-03`
- `/review-csv 2026-03`
- `/delete a1b2c`

## Test Bot Response
1. Start app with valid bot token.
2. In Telegram chat, run:
   - `/start`
   - `/set-passphrase <passphrase>` (first time)
   - `/unlock <passphrase>`
   - `/add 15000 food lunch`
   - `/review`
3. Check logs for `:event :update-handled`.

If no updates appear:
1. Clear webhook once:

```clojure
(require '[marksto.clj-tg-bot-api.core :as tg])
(def client (tg/->client {:bot-token "YOUR_BOT_TOKEN"}))
(tg/make-request! client :delete-webhook {})
```

2. Verify token:

```clojure
(tg/make-request! client :get-me)
```

3. Check service state:

```bash
curl http://localhost:8080/ready
# {"ready":true}
curl http://localhost:8080/metrics
# {"updates-polled":0,"updates-handled":0,"updates-skipped-duplicate":0,"handler-errors":0,"active-unlocked-sessions":0}
```

## HTTP API Structure
- Route handlers are in `src/protokin/api/routes.clj`.
- JSON response helper is in `src/protokin/api/response.clj`.
- HTTP component lifecycle remains in `src/protokin/system/http.clj`.
- Internal endpoint:
  - `GET /internal/unlocks` with header `x-admin-token: <ADMIN_TOKEN>`
  - returns current in-memory unlocked session count.

