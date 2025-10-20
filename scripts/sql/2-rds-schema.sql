-- PatternAlarm RDS Schema
-- Run after RDS deployment: psql -h <rds-endpoint> -U dbadmin -d patternalarm -f 2-rds-schema.sql

-- Table 1: Alert summary
CREATE TABLE fraud_alerts (
  alert_id SERIAL PRIMARY KEY,
  alert_type VARCHAR(50) NOT NULL,
  domain VARCHAR(20) NOT NULL,
  actor_id VARCHAR(100) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  fraud_score INT NOT NULL,
  transaction_count INT NOT NULL,
  total_amount DECIMAL(15, 2) NOT NULL,
  first_seen TIMESTAMP NOT NULL,
  last_seen TIMESTAMP NOT NULL,
  metadata JSONB NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);


-- Table 2: Transaction details (stores full TransactionEvent)
CREATE TABLE suspicious_transactions (
  id SERIAL PRIMARY KEY,
  alert_id INT NOT NULL REFERENCES fraud_alerts(alert_id) ON DELETE CASCADE,
  
  -- TransactionEvent fields (all stored flat)
  transaction_id VARCHAR(100) NOT NULL,
  domain VARCHAR(20) NOT NULL,
  test_id VARCHAR(100) NOT NULL,
  timestamp TIMESTAMP NOT NULL,
  actor_id VARCHAR(100) NOT NULL,
  amount DECIMAL(15, 2) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  ip_address VARCHAR(50) NOT NULL,
  
  pattern VARCHAR(50) NOT NULL,
  is_fraud BOOLEAN NOT NULL,
  sequence_position INT NOT NULL,
  
  -- Gaming fields
  player_id VARCHAR(100),
  game_id VARCHAR(100),
  item_type VARCHAR(50),
  item_name VARCHAR(100),
  payment_method VARCHAR(50),
  device_id VARCHAR(100),
  session_length_sec INT,
  
  -- Fintech fields
  account_from VARCHAR(100),
  account_to VARCHAR(100),
  transfer_type VARCHAR(50),
  country_from VARCHAR(10),
  country_to VARCHAR(10),
  purpose VARCHAR(100),
  
  -- Ecommerce fields
  user_id VARCHAR(100),
  cart_items TEXT,  -- JSON string
  shipping_address TEXT,
  billing_address TEXT,
  device_fingerprint VARCHAR(100),
  session_duration_sec INT,
  
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_suspicious_txns_alert ON suspicious_transactions(alert_id);
CREATE INDEX idx_suspicious_txns_actor ON suspicious_transactions(actor_id);