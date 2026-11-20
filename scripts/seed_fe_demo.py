"""
FE demo seed for Supabase Postgres.

Creates one company with ADMIN / HR / employees, attendance (full + partial months),
leaves (past/present/future + all statuses), advances, salary structures, holidays,
and pending thumb logs — enough for every FE screen.

Usage (from repo root, app can be running or stopped):
  python scripts/seed_fe_demo.py

All demo users password: Test@1234
Idempotent: deletes existing company "Acme Demo Pvt Ltd" first (CASCADE), keeps SUPERADMIN.
"""

from __future__ import annotations

import calendar
import pathlib
import random
import re
import sys
from datetime import date, datetime, time, timedelta
from decimal import Decimal

try:
    import bcrypt
except ImportError:
    import subprocess

    subprocess.check_call([sys.executable, "-m", "pip", "install", "bcrypt", "-q"])
    import bcrypt

try:
    import psycopg2
    from psycopg2.extras import execute_values
except ImportError:
    import subprocess

    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary", "-q"])
    import psycopg2
    from psycopg2.extras import execute_values

COMPANY_NAME = "Acme Demo Pvt Ltd"
PASSWORD = "Test@1234"
TODAY = date.today()
YEAR = TODAY.year
MONTH = TODAY.month

# Fixed mobiles for FE docs
MOBILES = {
    "admin": "9000000001",
    "hr": "9000000002",
    "emp_full": "9000000003",       # almost full present month
    "emp_sparse": "9000000004",     # many absents / missing days
    "emp_midjoin": "9000000005",    # joined mid-month
    "emp_leave": "9000000006",      # heavy leave usage
    "emp_half": "9000000007",       # half-days pattern
    "emp_new": "9000000008",        # probation, little history
}


def load_props() -> dict[str, str]:
    text = pathlib.Path("src/main/resources/application-local.properties").read_text(encoding="utf-8")
    out = {}
    for m in re.finditer(r"^([^=\s]+)=(.*)$", text, re.M):
        out[m.group(1).strip()] = m.group(2).strip()
    return out


def connect():
    props = load_props()
    url = props["spring.datasource.url"].replace("jdbc:postgresql://", "")
    hostport, db = url.split("/", 1)
    host, port = hostport.split(":")
    return psycopg2.connect(
        host=host,
        port=port,
        dbname=db,
        user=props["spring.datasource.username"],
        password=props["spring.datasource.password"],
        sslmode="require",
    )


def hash_password(plain: str) -> str:
    return bcrypt.hashpw(plain.encode(), bcrypt.gensalt(rounds=10)).decode()


def month_days(year: int, month: int) -> list[date]:
    last = calendar.monthrange(year, month)[1]
    return [date(year, month, d) for d in range(1, last + 1)]


def is_weekend(d: date) -> bool:
    return d.weekday() >= 5  # Sat/Sun


def prev_month(y: int, m: int) -> tuple[int, int]:
    if m == 1:
        return y - 1, 12
    return y, m - 1


def insert_returning(cur, sql: str, params) -> int:
    cur.execute(sql + " RETURNING id", params)
    return cur.fetchone()[0]


def wipe_demo_company(cur) -> None:
    cur.execute("SELECT id FROM companies WHERE name = %s", (COMPANY_NAME,))
    row = cur.fetchone()
    if not row:
        print("No existing demo company — fresh seed.")
        return
    cid = row[0]
    print(f"Removing existing demo company id={cid} …")
    # company CASCADE cleans most child tables via FKs on company_id / users
    cur.execute("DELETE FROM companies WHERE id = %s", (cid,))


def seed(conn) -> None:
    cur = conn.cursor()
    wipe_demo_company(cur)

    pwd = hash_password(PASSWORD)
    cur.execute("SELECT id, name FROM roles")
    roles = {n: i for i, n in cur.fetchall()}
    cur.execute("SELECT id, name FROM user_statuses")
    statuses = {n: i for i, n in cur.fetchall()}

    # ── Company + settings ─────────────────────────────────────
    company_id = insert_returning(
        cur,
        """
        INSERT INTO companies (name, address, is_active)
        VALUES (%s, %s, TRUE)
        """,
        (COMPANY_NAME, "Pune, Maharashtra, India"),
    )
    secret = "demo-device-secret-" + "".join(random.choices("abcdef0123456789", k=16))
    cur.execute(
        """
        INSERT INTO company_settings (
          company_id, shift_start_time, shift_end_time, shift_hours, half_day_hours,
          overtime_after_hours, late_mark_after_minutes, week_off_day, leave_count_type,
          device_brand, device_secret, device_serial
        ) VALUES (
          %s, '09:30:00', '18:30:00', 9.00, 4.50, 9.00, 15,
          'SATURDAY,SUNDAY', 'EXCLUDE_WEEK_OFF_AND_HOLIDAYS',
          'ZKTECO', %s, 'DEMO-SN-001'
        )
        """,
        (company_id, secret),
    )

    # ── Org structure ──────────────────────────────────────────
    depts = {}
    for name in ("Engineering", "Human Resources", "Sales", "Operations"):
        depts[name] = insert_returning(
            cur,
            "INSERT INTO departments (company_id, name) VALUES (%s, %s)",
            (company_id, name),
        )
    desigs = {}
    for name in (
        "Software Engineer",
        "HR Executive",
        "Sales Executive",
        "Operations Manager",
        "Team Lead",
    ):
        desigs[name] = insert_returning(
            cur,
            "INSERT INTO designations (company_id, name) VALUES (%s, %s)",
            (company_id, name),
        )

    # ── Holidays (past + upcoming) ─────────────────────────────
    holidays = [
        (date(YEAR, 1, 26), "Republic Day"),
        (date(YEAR, 8, 15), "Independence Day"),
        (date(YEAR, 10, 2), "Gandhi Jayanti"),
    ]
    # next weekday ~2 weeks out as company holiday
    upcoming = TODAY + timedelta(days=14)
    while is_weekend(upcoming):
        upcoming += timedelta(days=1)
    holidays.append((upcoming, "Company Foundation Day"))
    for hd, name in holidays:
        if hd.year != YEAR:
            continue
        cur.execute(
            """
            INSERT INTO holidays (company_id, name, holiday_date)
            VALUES (%s, %s, %s)
            ON CONFLICT (company_id, holiday_date) DO NOTHING
            """,
            (company_id, name, hd),
        )

    # ── Leave types + helpers ──────────────────────────────────
    leave_types = {
        "Casual Leave": insert_returning(
            cur,
            """
            INSERT INTO leave_types (
              company_id, name, annual_quota, is_paid, allow_half_day, carry_forward, max_carry_forward_days
            ) VALUES (%s, 'Casual Leave', 12, TRUE, TRUE, FALSE, 0)
            """,
            (company_id,),
        ),
        "Sick Leave": insert_returning(
            cur,
            """
            INSERT INTO leave_types (
              company_id, name, annual_quota, is_paid, allow_half_day, carry_forward, max_carry_forward_days
            ) VALUES (%s, 'Sick Leave', 8, TRUE, TRUE, FALSE, 0)
            """,
            (company_id,),
        ),
        "Earned Leave": insert_returning(
            cur,
            """
            INSERT INTO leave_types (
              company_id, name, annual_quota, is_paid, allow_half_day, carry_forward, max_carry_forward_days
            ) VALUES (%s, 'Earned Leave', 15, TRUE, FALSE, TRUE, 5)
            """,
            (company_id,),
        ),
    }

    def add_balances(user_id: int, used_cl: float = 0, used_sl: float = 0, used_el: float = 0):
        for name, total, used in (
            ("Casual Leave", 12, used_cl),
            ("Sick Leave", 8, used_sl),
            ("Earned Leave", 15, used_el),
        ):
            cur.execute(
                """
                INSERT INTO leave_balances (
                  user_id, leave_type_id, year, total_days, used_days, remaining_days
                ) VALUES (%s, %s, %s, %s, %s, %s)
                """,
                (
                    user_id,
                    leave_types[name],
                    YEAR,
                    Decimal(str(total)),
                    Decimal(str(used)),
                    Decimal(str(total - used)),
                ),
            )

    def add_user(
        *,
        key: str,
        full_name: str,
        role: str,
        dept: str,
        desig: str,
        emp_code: str,
        status: str = "Active",
        joining: date | None = None,
        email: str | None = None,
        manager_id: int | None = None,
    ) -> int:
        uid = insert_returning(
            cur,
            """
            INSERT INTO users (
              company_id, department_id, designation_id, status_id,
              emp_code, full_name, mobile, email, password_hash,
              salary_type, joining_date, manager_id
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'MONTHLY',%s,%s)
            """,
            (
                company_id,
                depts[dept],
                desigs[desig],
                statuses[status],
                emp_code,
                full_name,
                MOBILES[key],
                email or f"{key.replace('_', '.')}@acmedemo.test",
                pwd,
                joining or date(YEAR, 1, 15),
                manager_id,
            ),
        )
        cur.execute(
            "INSERT INTO user_roles (user_id, role_id) VALUES (%s, %s)",
            (uid, roles[role]),
        )
        return uid

    # ── People ─────────────────────────────────────────────────
    admin_id = add_user(
        key="admin",
        full_name="Rahul Shah",
        role="ADMIN",
        dept="Operations",
        desig="Operations Manager",
        emp_code="EMP001",
        joining=date(YEAR - 1, 4, 1),
    )
    hr_id = add_user(
        key="hr",
        full_name="Priya Mehta",
        role="HR",
        dept="Human Resources",
        desig="HR Executive",
        emp_code="EMP002",
        joining=date(YEAR - 1, 6, 1),
        manager_id=admin_id,
    )
    emp_full = add_user(
        key="emp_full",
        full_name="Amit Kumar",
        role="EMPLOYEE",
        dept="Engineering",
        desig="Software Engineer",
        emp_code="EMP003",
        joining=date(YEAR - 1, 1, 10),
        manager_id=admin_id,
    )
    emp_sparse = add_user(
        key="emp_sparse",
        full_name="Sneha Patil",
        role="EMPLOYEE",
        dept="Sales",
        desig="Sales Executive",
        emp_code="EMP004",
        joining=date(YEAR - 1, 8, 1),
        manager_id=admin_id,
    )
    emp_mid = add_user(
        key="emp_midjoin",
        full_name="Vikram Joshi",
        role="EMPLOYEE",
        dept="Engineering",
        desig="Software Engineer",
        emp_code="EMP005",
        joining=date(YEAR, MONTH, 12) if TODAY.day >= 12 else date(YEAR, MONTH, 1),
        manager_id=admin_id,
    )
    emp_leave = add_user(
        key="emp_leave",
        full_name="Neha Desai",
        role="EMPLOYEE",
        dept="Engineering",
        desig="Team Lead",
        emp_code="EMP006",
        joining=date(YEAR - 2, 3, 1),
        manager_id=admin_id,
    )
    emp_half = add_user(
        key="emp_half",
        full_name="Rohit Kulkarni",
        role="EMPLOYEE",
        dept="Sales",
        desig="Sales Executive",
        emp_code="EMP007",
        joining=date(YEAR - 1, 11, 1),
        manager_id=hr_id,
    )
    emp_new = add_user(
        key="emp_new",
        full_name="Ananya Iyer",
        role="EMPLOYEE",
        dept="Engineering",
        desig="Software Engineer",
        emp_code="EMP008",
        status="Probation",
        joining=TODAY - timedelta(days=20),
        manager_id=emp_leave,
    )

    add_balances(admin_id, used_cl=1)
    add_balances(hr_id, used_cl=2, used_sl=1)
    add_balances(emp_full, used_cl=1)
    add_balances(emp_sparse, used_cl=3, used_sl=2)
    add_balances(emp_mid, used_cl=0)
    add_balances(emp_leave, used_cl=5, used_sl=2, used_el=3)
    add_balances(emp_half, used_cl=2)
    add_balances(emp_new, used_cl=0)

    # ── Salary structures ──────────────────────────────────────
    for uid, basic in (
        (admin_id, 80000),
        (hr_id, 45000),
        (emp_full, 55000),
        (emp_sparse, 40000),
        (emp_mid, 50000),
        (emp_leave, 70000),
        (emp_half, 38000),
        (emp_new, 42000),
    ):
        cur.execute(
            """
            INSERT INTO salary_structures (
              user_id, basic, hra, allowances, pf_deduction, other_deductions,
              overtime_rate, effective_from
            ) VALUES (%s,%s,%s,%s,%s,0,%s,%s)
            """,
            (
                uid,
                Decimal(basic),
                Decimal(basic) * Decimal("0.4"),
                Decimal(5000),
                Decimal(1800),
                Decimal(300),
                date(YEAR, 1, 1),
            ),
        )

    # ── Leave requests (past / current / future + all statuses) ─
    def add_leave(
        user_id,
        leave_name,
        frm,
        to,
        days,
        status,
        reason,
        *,
        half=False,
        half_type=None,
        reject=None,
        actioned_by=None,
        actioned_at=None,
    ):
        cur.execute(
            """
            INSERT INTO leave_requests (
              user_id, leave_type_id, from_date, to_date, total_days, reason, status,
              actioned_by, reject_reason, actioned_at, is_half_day, half_day_type
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            """,
            (
                user_id,
                leave_types[leave_name],
                frm,
                to,
                Decimal(str(days)),
                reason,
                status,
                actioned_by,
                reject,
                actioned_at,
                half,
                half_type,
            ),
        )

    # Past approved
    add_leave(
        emp_leave,
        "Casual Leave",
        TODAY - timedelta(days=40),
        TODAY - timedelta(days=38),
        3,
        "APPROVED",
        "Family function",
        actioned_by=hr_id,
        actioned_at=datetime.combine(TODAY - timedelta(days=41), time(11, 0)),
    )
    # Past rejected
    add_leave(
        emp_sparse,
        "Earned Leave",
        TODAY - timedelta(days=25),
        TODAY - timedelta(days=20),
        5,
        "REJECTED",
        "Long vacation",
        actioned_by=admin_id,
        actioned_at=datetime.combine(TODAY - timedelta(days=26), time(10, 0)),
        reject="Peak sales month — please reapply later",
    )
    # Past cancelled
    add_leave(
        emp_full,
        "Sick Leave",
        TODAY - timedelta(days=15),
        TODAY - timedelta(days=15),
        1,
        "CANCELLED",
        "Feeling unwell (cancelled)",
        actioned_by=emp_full,
        actioned_at=datetime.combine(TODAY - timedelta(days=15), time(8, 0)),
    )
    # Current pending (for HR/ADMIN queue — not own for HR)
    add_leave(
        emp_half,
        "Casual Leave",
        TODAY + timedelta(days=2),
        TODAY + timedelta(days=3),
        2,
        "PENDING",
        "Personal work",
    )
    add_leave(
        emp_leave,
        "Sick Leave",
        TODAY + timedelta(days=1),
        TODAY + timedelta(days=1),
        1,
        "PENDING",
        "Doctor appointment",
    )
    # HR own pending (should NOT show in HR pending queue)
    add_leave(
        hr_id,
        "Casual Leave",
        TODAY + timedelta(days=10),
        TODAY + timedelta(days=10),
        1,
        "PENDING",
        "HR personal leave",
    )
    # Future approved
    add_leave(
        emp_full,
        "Earned Leave",
        TODAY + timedelta(days=20),
        TODAY + timedelta(days=22),
        3,
        "APPROVED",
        "Planned trip",
        actioned_by=hr_id,
        actioned_at=datetime.now(),
    )
    # Half-day pending
    add_leave(
        emp_half,
        "Casual Leave",
        TODAY + timedelta(days=5),
        TODAY + timedelta(days=5),
        0.5,
        "PENDING",
        "Half day morning",
        half=True,
        half_type="MORNING",
    )

    # ── Advances ───────────────────────────────────────────────
    def add_advance(user_id, amount, reason, status, *, reject=None, month=None, year=None, recovered=False, by=None):
        cur.execute(
            """
            INSERT INTO advances (
              user_id, amount, reason, reject_reason, status,
              deduct_from_month, deduct_from_year, is_recovered, approved_by, actioned_at
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            """,
            (
                user_id,
                Decimal(str(amount)),
                reason,
                reject,
                status,
                month,
                year,
                recovered,
                by,
                datetime.now() if status != "PENDING" else None,
            ),
        )

    add_advance(emp_sparse, 5000, "Medical emergency", "APPROVED", month=MONTH, year=YEAR, by=admin_id)
    add_advance(emp_leave, 10000, "Home repair", "APPROVED", month=MONTH, year=YEAR, recovered=True, by=admin_id)
    add_advance(emp_half, 3000, "Travel advance", "REJECTED", reject="Insufficient justification", by=hr_id)
    add_advance(emp_full, 2000, "Festival advance", "PENDING")
    add_advance(emp_new, 1500, "Laptop stand / accessories", "PENDING")
    # Past month approved
    py, pm = prev_month(YEAR, MONTH)
    add_advance(emp_full, 4000, "Last month advance", "APPROVED", month=pm, year=py, recovered=True, by=admin_id)
    # HR own pending (excluded from HR pending list)
    add_advance(hr_id, 2500, "HR own advance request", "PENDING")

    # ── Attendance helpers ─────────────────────────────────────
    holiday_set = {h[0] for h in holidays}

    def upsert_attendance(user_id, d: date, status: str, *, check_in=None, check_out=None, source="WEB", worked=0, ot=0):
        cur.execute(
            """
            INSERT INTO attendance (
              user_id, company_id, attendance_date, check_in, check_out,
              worked_hours, overtime_hours, status, source, is_manual, is_auto_checkout
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,FALSE,FALSE)
            ON CONFLICT (user_id, attendance_date) DO NOTHING
            RETURNING id
            """,
            (
                user_id,
                company_id,
                d,
                check_in,
                check_out,
                Decimal(str(worked)),
                Decimal(str(ot)),
                status,
                source,
            ),
        )
        row = cur.fetchone()
        return row[0] if row else None

    def present_day(user_id, d: date, late=False):
        cin = datetime.combine(d, time(10, 5 if late else 9, 35))
        cout = datetime.combine(d, time(18, 40))
        aid = upsert_attendance(
            user_id, d, "PRESENT", check_in=cin, check_out=cout, worked=9.0, source="WEB"
        )
        if aid:
            cur.execute(
                """
                INSERT INTO attendance_punches (attendance_id, user_id, punch_time, punch_type, source)
                VALUES (%s,%s,%s,'IN','WEB'), (%s,%s,%s,'OUT','WEB')
                """,
                (aid, user_id, cin, aid, user_id, cout),
            )

    def seed_month_for_user(user_id, year, month, pattern: str, joining: date | None = None):
        """
        pattern:
          full   — present all working days up to today
          sparse — ~40% present, rest absent (no row or ABSENT)
          leavey — some ON_LEAVE days
          half   — mix of PRESENT + HALF_DAY
          mid    — only from joining date
        """
        for d in month_days(year, month):
            if joining and d < joining:
                continue
            if d > TODAY:
                continue
            if is_weekend(d):
                upsert_attendance(user_id, d, "WEEK_OFF")
                continue
            if d in holiday_set:
                upsert_attendance(user_id, d, "HOLIDAY")
                continue

            if pattern == "full":
                present_day(user_id, d, late=(d.day % 7 == 0))
            elif pattern == "sparse":
                if d.day % 3 == 0:
                    upsert_attendance(user_id, d, "ABSENT")
                elif d.day % 2 == 0:
                    present_day(user_id, d)
                # else: missing row = not marked
            elif pattern == "leavey":
                if d.day in (5, 6, 7):
                    upsert_attendance(user_id, d, "ON_LEAVE")
                else:
                    present_day(user_id, d)
            elif pattern == "half":
                if d.day % 4 == 0:
                    cin = datetime.combine(d, time(9, 30))
                    cout = datetime.combine(d, time(13, 30))
                    aid = upsert_attendance(
                        user_id, d, "HALF_DAY", check_in=cin, check_out=cout, worked=4.0
                    )
                    if aid:
                        cur.execute(
                            """
                            INSERT INTO attendance_punches (attendance_id, user_id, punch_time, punch_type, source)
                            VALUES (%s,%s,%s,'IN','WEB'), (%s,%s,%s,'OUT','WEB')
                            """,
                            (aid, user_id, cin, aid, user_id, cout),
                        )
                else:
                    present_day(user_id, d)
            elif pattern == "mid":
                present_day(user_id, d)

    py, pm = prev_month(YEAR, MONTH)
    # Previous month history
    seed_month_for_user(emp_full, py, pm, "full", date(YEAR - 1, 1, 10))
    seed_month_for_user(emp_sparse, py, pm, "sparse", date(YEAR - 1, 8, 1))
    seed_month_for_user(emp_leave, py, pm, "leavey", date(YEAR - 2, 3, 1))
    seed_month_for_user(hr_id, py, pm, "full", date(YEAR - 1, 6, 1))

    # Current month
    seed_month_for_user(emp_full, YEAR, MONTH, "full", date(YEAR - 1, 1, 10))
    seed_month_for_user(emp_sparse, YEAR, MONTH, "sparse", date(YEAR - 1, 8, 1))
    seed_month_for_user(emp_leave, YEAR, MONTH, "leavey", date(YEAR - 2, 3, 1))
    seed_month_for_user(emp_half, YEAR, MONTH, "half", date(YEAR - 1, 11, 1))
    seed_month_for_user(emp_mid, YEAR, MONTH, "mid", date(YEAR, MONTH, 12) if TODAY.day >= 12 else date(YEAR, MONTH, 1))
    seed_month_for_user(hr_id, YEAR, MONTH, "full", date(YEAR - 1, 6, 1))
    seed_month_for_user(admin_id, YEAR, MONTH, "full", date(YEAR - 1, 4, 1))
    # new joiner: only last few days
    seed_month_for_user(emp_new, YEAR, MONTH, "mid", TODAY - timedelta(days=20))

    # Today still checked-in (no checkout) for one employee — FE check-out CTA
    if not is_weekend(TODAY) and TODAY not in holiday_set:
        cin = datetime.combine(TODAY, time(9, 40))
        cur.execute("DELETE FROM attendance WHERE user_id=%s AND attendance_date=%s", (emp_full, TODAY))
        aid = upsert_attendance(emp_full, TODAY, "PRESENT", check_in=cin, check_out=None, worked=0, source="WEB")
        if aid:
            cur.execute(
                """
                INSERT INTO attendance_punches (attendance_id, user_id, punch_time, punch_type, source)
                VALUES (%s,%s,%s,'IN','WEB')
                """,
                (aid, emp_full, cin),
            )

    # ── Thumb logs pending (ops badge) ─────────────────────────
    for i, emp_code in enumerate(("EMP003", "EMP004", "EMP999")):
        cur.execute(
            """
            INSERT INTO thumb_logs (company_id, device_emp_id, punch_time, punch_type, is_processed)
            VALUES (%s, %s, %s, %s, FALSE)
            """,
            (
                company_id,
                emp_code,
                datetime.combine(TODAY, time(9, 10 + i)),
                "IN" if i % 2 == 0 else "OUT",
            ),
        )

    # ── Salary payment sample (prev month DRAFT + one PAID) ────
    cur.execute("SELECT id FROM salary_structures WHERE user_id=%s", (emp_full,))
    struct_id = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO salary_payments (
          user_id, salary_structure_id, month, year, working_days,
          present_days, absent_days, half_days, paid_leave_days, unpaid_leave_days,
          overtime_hours, overtime_pay, gross_salary, advance_deduction,
          total_deductions, net_salary, status, generated_by
        ) VALUES (
          %s,%s,%s,%s,22, 20,1,1,0,0, 2,600, 62000,4000, 5800, 56200, 'DRAFT', %s
        )
        """,
        (emp_full, struct_id, pm, py, hr_id),
    )
    cur.execute("SELECT id FROM salary_structures WHERE user_id=%s", (emp_leave,))
    struct2 = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO salary_payments (
          user_id, salary_structure_id, month, year, working_days,
          present_days, absent_days, half_days, paid_leave_days, unpaid_leave_days,
          overtime_hours, overtime_pay, gross_salary, advance_deduction,
          total_deductions, net_salary, status, paid_on, generated_by
        ) VALUES (
          %s,%s,%s,%s,22, 18,2,0,2,0, 0,0, 78000,0, 3600, 74400, 'PAID', %s, %s
        )
        """,
        (emp_leave, struct2, pm, py, date(py, pm, 28) if pm != 2 else date(py, pm, 27), admin_id),
    )

    # Salary adjustment sample
    cur.execute(
        """
        INSERT INTO salary_adjustments (
          user_id, company_id, month, year, type, amount, reason, created_by
        ) VALUES (%s,%s,%s,%s,'BONUS',5000,'Diwali performance bonus',%s)
        """,
        (emp_full, company_id, MONTH, YEAR, admin_id),
    )

    conn.commit()
    cur.close()

    print("\n=== FE DEMO SEED COMPLETE ===")
    print(f"Company: {COMPANY_NAME} (id={company_id})")
    print(f"Password for all below: {PASSWORD}\n")
    print(f"{'Role':<12} {'Name':<18} {'Mobile':<12} {'EmpCode':<8} Notes")
    print("-" * 78)
    print(f"{'SUPERADMIN':<12} {'Platform':<18} {'9000000000':<12} {'-':<8} already seeded")
    print(f"{'ADMIN':<12} {'Rahul Shah':<18} {MOBILES['admin']:<12} {'EMP001':<8} full company access")
    print(f"{'HR':<12} {'Priya Mehta':<18} {MOBILES['hr']:<12} {'EMP002':<8} ops + own pending leave/advance")
    print(f"{'EMPLOYEE':<12} {'Amit Kumar':<18} {MOBILES['emp_full']:<12} {'EMP003':<8} full-month present; checked-in today")
    print(f"{'EMPLOYEE':<12} {'Sneha Patil':<18} {MOBILES['emp_sparse']:<12} {'EMP004':<8} sparse attendance")
    print(f"{'EMPLOYEE':<12} {'Vikram Joshi':<18} {MOBILES['emp_midjoin']:<12} {'EMP005':<8} mid-month joiner")
    print(f"{'EMPLOYEE':<12} {'Neha Desai':<18} {MOBILES['emp_leave']:<12} {'EMP006':<8} leave-heavy + paid salary")
    print(f"{'EMPLOYEE':<12} {'Rohit Kulkarni':<18} {MOBILES['emp_half']:<12} {'EMP007':<8} half-days + pending leaves")
    print(f"{'EMPLOYEE':<12} {'Ananya Iyer':<18} {MOBILES['emp_new']:<12} {'EMP008':<8} probation / new joiner")
    print("\nCovers: home/ops, employees, attendance, leaves (all statuses), advances,")
    print("payroll/salary, holidays, settings, thumb pending, filters.")


def main():
    conn = connect()
    try:
        seed(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
