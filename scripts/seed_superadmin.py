import re
import pathlib
import sys

try:
    import psycopg2
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary", "-q"])
    import psycopg2

props = pathlib.Path("src/main/resources/application-local.properties").read_text(encoding="utf-8")


def get(key: str) -> str:
    m = re.search(rf"^{re.escape(key)}=(.*)$", props, re.M)
    if not m:
        raise SystemExit(f"Missing {key}")
    return m.group(1).strip()


url = get("spring.datasource.url").replace("jdbc:postgresql://", "")
hostport, db = url.split("/", 1)
host, port = hostport.split(":")
user = get("spring.datasource.username")
password = get("spring.datasource.password")

# Test@1234
pwd_hash = "$2b$10$WH7EhJJzciqAfGIRKyTnKeRL7Eryh.aBu/nKbd.2Fq09F9Ote2Ev."

conn = psycopg2.connect(
    host=host, port=port, dbname=db, user=user, password=password, sslmode="require"
)
cur = conn.cursor()
cur.execute(
    """
    INSERT INTO users (status_id, full_name, mobile, password_hash)
    VALUES (1, 'Platform Superadmin', '9000000000', %s)
    ON CONFLICT (mobile) DO UPDATE SET password_hash = EXCLUDED.password_hash
    RETURNING id
    """,
    (pwd_hash,),
)
uid = cur.fetchone()[0]
cur.execute("SELECT id FROM roles WHERE name=%s", ("SUPERADMIN",))
rid = cur.fetchone()[0]
cur.execute(
    """
    INSERT INTO user_roles (user_id, role_id)
    SELECT %s, %s
    WHERE NOT EXISTS (
      SELECT 1 FROM user_roles WHERE user_id=%s AND role_id=%s
    )
    """,
    (uid, rid, uid, rid),
)
conn.commit()
print(f"SEEDED superadmin user_id={uid} mobile=9000000000 password=Test@1234")
cur.close()
conn.close()
