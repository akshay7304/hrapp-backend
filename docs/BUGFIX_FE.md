# HR App — Bug fixes (BE done → FE action)

Base path: `/api/v1`. Status as of Nov 2026.

## Auth / employees

| #       | Bug                | BE change                                                                        | FE action                                                                                  |
| ------- | ------------------ | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| **1.1** | Short access token | Access **1 day**, refresh **7 days**. Login returns `accessTokenExpiresIn` (ms). | Use refresh before expiry; don’t hardcode 15 min.                                          |
| **2.1** | Manual `empCode`   | `empCode` optional on `POST /employees`. BE returns `EMP001`, `EMP002`…          | Omit `empCode` on create; show value from response.                                        |
| **2.2** | Avatar / photo     | Response field `avatar` = profile pic URL or `null`. (No `avatarColor`.)         | If `avatar` null → show initials; else `<img src={avatar}>`. Upload API not in this batch. |

## Attendance / leave

| # | Bug | BE change | FE action |
|---|-----|-----------|-----------|
| **3.2** | Regularization times wrong | DateTimes serialized in **IST** (not UTC shift). | Send/display local times; refetch attendance after approve. |
| **4.1** | HR own leave missing | `GET /leaves/history` (+ `/leaves/my`) for any role; `year` required, `month` optional. | Use `/leaves/history` for “my leaves”, not `/pending`. |
| **4.2** | Self-approve leave | Own requests excluded from `/leaves/pending`; action blocked. | Don’t expect own leave in pending queue. |
| **4.3** | Mid-year full quota | Calendar pro-rata on balance **init** (e.g. May → 8/12). Existing rows not rewritten. | Show `totalDays` from balances API as-is. |

## Advances / payroll

| # | Bug | BE change | FE action |
|---|-----|-----------|-----------|
| **5.1** | Advances oldest-first | Lists sorted **newest first** (`createdAt DESC`). | No sort client-side required. |
| **5.2** | Can HR request advance? | **Yes** — HR/ADMIN/EMPLOYEE can `POST /advances`. Self-approve still blocked. | Keep “Request advance” for HR. |
| **7.2** | Salary structure E2E | Aliases: `GET /employees/{id}/salary-structure`, `GET /employees/{id}/adjustments?month=&year=` (omit both for all). Also `/payroll/...`. | Prefer employee aliases on details page; empty `[]` = none. |

## Admin / company / settings

| # | Bug | BE change | FE action |
|---|-----|-----------|-----------|
| **6.3** | Single week-off only | `weekOffDays: ["SATURDAY","SUNDAY"]` on settings GET/PUT. Legacy `weekOffDay` kept. Path: `/company-settings/company/{id}`. | Send **array** `weekOffDays`; read array for multi-select. |
| **6.4** | Company 409 unclear | Conflict returns field errors (e.g. `errors.adminMobile`). | Map `errors.`* to form fields. |
| **7.3** | No admin APIs | `GET/POST /companies/{companyId}/admins`, `DELETE .../admins/{userId}`. Multi-admin OK; last admin cannot revoke. | Wire admin list / assign / revoke UI. |

## List filters / leave types

| # | Bug | BE change | FE action |
|---|-----|-----------|-----------|
| **7.1** | No list filters | `GET /employees?departmentId=&statusId=&search=` · `GET /advances/company?status=&employeeId=` · `GET /leaves/company?month=&year=&status=&employeeId=` | Pass query params instead of client-only filter. |
| **8.1** | Leave-type fields missing | `allowHalfDay`, `carryForward`, `maxCarryForwardDays` on leave-type request/response. | Bind these on leave-type forms. |
| **8.2** | Delete leave type orphans | Delete with balances → **409**. | Show message; don’t delete if balances exist. |
| **8.3** | Quota change vs balances | Updating `annualQuota` does **not** rewrite existing balances (new hires / future init only). | Don’t expect old balances to change after quota edit. |

## Dashboards (already shipped)

| API | FE action |
|-----|-----------|
| `GET /dashboard/employee` | Employee home widgets. |
| `GET /dashboard/ops` | ADMIN/HR pending counts + today summary (incl. thumb pending count). |

## P0 security (fixed — rebuild)

| Item | FE action |
|------|-----------|
| CORS | `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000,http://localhost:5173`) |
| Settings secret | `deviceSecret` **never returned** — use `deviceSecretConfigured`. Write-only on PUT. |
| `deviceSerial` | Optional biometric SN for routing (not employee enrol id) |
| Create roles | ADMIN → EMPLOYEE/HR only; HR → EMPLOYEE only; ADMIN via `/companies/{id}/admins` |

## Remaining gaps

| Sev | Item | FE until BE fixes |
|-----|------|-------------------|
| P1 | `/advances/pending` includes self | Hide approve on own rows |
| P1 | No avatar upload | `avatar` null → initials |
| P1 | Default password = mobile | Force change-password on first login |
