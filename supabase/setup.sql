CREATE SCHEMA IF NOT EXISTS risk_demo;
CREATE TABLE IF NOT EXISTS risk_demo.accounts (
  id VARCHAR(40) PRIMARY KEY,
  display_name VARCHAR(120) NOT NULL,
  scenario VARCHAR(80) NOT NULL,
  description VARCHAR(1000) NOT NULL
);
CREATE TABLE IF NOT EXISTS risk_demo.transactions (
  id VARCHAR(60) PRIMARY KEY,
  account_id VARCHAR(40) NOT NULL REFERENCES risk_demo.accounts(id),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  direction VARCHAR(3) NOT NULL CHECK (direction IN ('IN', 'OUT')),
  amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
  counterparty VARCHAR(80) NOT NULL,
  description VARCHAR(300) NOT NULL
);
CREATE INDEX IF NOT EXISTS tx_account_time ON risk_demo.transactions(account_id, occurred_at);
CREATE TABLE IF NOT EXISTS risk_demo.reviews (
  id VARCHAR(40) PRIMARY KEY,
  account_id VARCHAR(40) NOT NULL REFERENCES risk_demo.accounts(id),
  status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_REVIEW', 'EXPLAINED', 'ESCALATED')),
  note VARCHAR(2000) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS review_account_time ON risk_demo.reviews(account_id, created_at);
CREATE TABLE IF NOT EXISTS risk_demo.imports (
  account_id VARCHAR(40) PRIMARY KEY REFERENCES risk_demo.accounts(id),
  review_as_of TIMESTAMP WITH TIME ZONE NOT NULL,
  transaction_count INTEGER NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Server-only demo tables. Keep risk_demo out of exposed Data API schemas.
ALTER TABLE risk_demo.accounts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON risk_demo.accounts FROM anon, authenticated;
ALTER TABLE risk_demo.transactions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON risk_demo.transactions FROM anon, authenticated;
ALTER TABLE risk_demo.reviews ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON risk_demo.reviews FROM anon, authenticated;
ALTER TABLE risk_demo.imports ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON risk_demo.imports FROM anon, authenticated;
