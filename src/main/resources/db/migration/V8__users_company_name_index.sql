-- Employee directory: WHERE company_id = ? ORDER BY full_name, id
CREATE INDEX IF NOT EXISTS idx_users_company_name ON users (company_id, full_name, id);
