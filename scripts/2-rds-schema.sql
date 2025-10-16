-- PatternAlarm RDS Schema
-- Run after RDS deployment: psql -h <rds-endpoint> -U dbadmin -d patternalarm -f 2-rds-schema.sql

-- Table 1: Fraud Alerts (parent)
CREATE TABLE IF NOT EXISTS fraud_alerts (
  alert_id SERIAL PRIMARY KEY,
  alert_type VARCHAR(50) NOT NULL,
  
  -- Queryable fields
  actor_id VARCHAR(100) NOT NULL,
  domain VARCHAR(20) NOT NULL,
  
  -- Analysis results
  fraud_score INTEGER NOT NULL,
  severity VARCHAR(20) NOT NULL,
  
  -- Aggregations
  transaction_count INTEGER NOT NULL,
  total_amount DECIMAL(12,2),
  first_seen TIMESTAMPTZ NOT NULL,
  last_seen TIMESTAMPTZ NOT NULL,
  
  -- Extra context
  metadata JSONB,
  
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alerts_actor ON fraud_alerts(actor_id);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON fraud_alerts(severity, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_domain ON fraud_alerts(domain);
CREATE INDEX IF NOT EXISTS idx_alerts_score ON fraud_alerts(fraud_score DESC);

-- Table 2: Suspicious Transactions (child)
CREATE TABLE IF NOT EXISTS suspicious_transactions (
  transaction_id VARCHAR(50) PRIMARY KEY,
  alert_id INTEGER NOT NULL,
  
  -- Queryable fields
  actor_id VARCHAR(100) NOT NULL,
  domain VARCHAR(20) NOT NULL,
  timestamp TIMESTAMPTZ NOT NULL,
  amount DECIMAL(12,2),
  
  -- Raw transaction data
  transaction_data JSONB NOT NULL,
  
  FOREIGN KEY (alert_id) REFERENCES fraud_alerts(alert_id)
);

CREATE INDEX IF NOT EXISTS idx_suspicious_alert ON suspicious_transactions(alert_id);
CREATE INDEX IF NOT EXISTS idx_suspicious_actor ON suspicious_transactions(actor_id);
CREATE INDEX IF NOT EXISTS idx_suspicious_domain_time ON suspicious_transactions(domain, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_suspicious_amount ON suspicious_transactions(amount DESC);

-- Verification
SELECT 'Schema created successfully!' as status;