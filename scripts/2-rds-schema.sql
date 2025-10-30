-- PatternAlarm RDS Schema - UPDATED for Flink Sink Compatibility
-- Run: psql -h localhost -p 5433 -U dbadmin -d patternalarm -f 2-rds-schema-v2.sql

-- ============================================================================
-- DROP EXISTING TABLES (if needed)
-- ============================================================================
-- Uncomment if you want to recreate from scratch
-- DROP TABLE IF EXISTS suspicious_transactions CASCADE;
-- DROP TABLE IF EXISTS fraud_alerts CASCADE;

-- ============================================================================
-- Table 1: Fraud Alerts (Extended Schema)
-- ============================================================================
CREATE TABLE fraud_alerts (
  alert_id SERIAL PRIMARY KEY,
  
  -- Core alert fields
  alert_type VARCHAR(50) NOT NULL,
  domain VARCHAR(20) NOT NULL,
  actor_id VARCHAR(100) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  fraud_score INT NOT NULL,
  transaction_count INT NOT NULL,
  total_amount DECIMAL(15, 2) NOT NULL,
  first_seen TIMESTAMP NOT NULL,
  last_seen TIMESTAMP NOT NULL,
  
  -- ✅ Additional analytics fields
  window_seconds BIGINT,
  baseline_avg DOUBLE PRECISION,
  patterns_detected TEXT[],  -- PostgreSQL array
  confidence INT,
  model_version VARCHAR(50),
  inference_time_ms INT,
  
  -- ✅ Gaming domain fields
  player_id VARCHAR(100),
  game_id VARCHAR(100),
  item_type VARCHAR(50),
  item_name VARCHAR(100),
  session_length_sec INT,
  
  -- ✅ Fintech domain fields
  account_from VARCHAR(100),
  account_to VARCHAR(100),
  transfer_type VARCHAR(50),
  country_from VARCHAR(10),
  country_to VARCHAR(10),
  purpose VARCHAR(100),
  
  -- ✅ Ecommerce domain fields
  user_id VARCHAR(100),
  cart_items TEXT,
  shipping_address TEXT,
  billing_address TEXT,
  device_fingerprint VARCHAR(100),
  session_duration_sec INT,
  
  -- ✅ Common fields
  payment_method VARCHAR(50),
  device_id VARCHAR(100),
  ip_address VARCHAR(50),
  
  created_at TIMESTAMP DEFAULT NOW()
);

-- ============================================================================
-- Table 2: Suspicious Transactions
-- ============================================================================
CREATE TABLE suspicious_transactions (
  id SERIAL PRIMARY KEY,
  alert_id INT NOT NULL REFERENCES fraud_alerts(alert_id) ON DELETE CASCADE,
  
  -- Core transaction fields
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
  cart_items TEXT,
  shipping_address TEXT,
  billing_address TEXT,
  device_fingerprint VARCHAR(100),
  session_duration_sec INT,
  
  created_at TIMESTAMP DEFAULT NOW()
);

-- ============================================================================
-- INDEXES for Performance
-- ============================================================================
CREATE INDEX idx_alerts_actor ON fraud_alerts(actor_id);
CREATE INDEX idx_alerts_domain ON fraud_alerts(domain);
CREATE INDEX idx_alerts_severity ON fraud_alerts(severity);
CREATE INDEX idx_alerts_created ON fraud_alerts(created_at DESC);

CREATE INDEX idx_suspicious_txns_alert ON suspicious_transactions(alert_id);
CREATE INDEX idx_suspicious_txns_actor ON suspicious_transactions(actor_id);
CREATE INDEX idx_suspicious_txns_timestamp ON suspicious_transactions(timestamp DESC);

-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================
-- Check tables exist
SELECT table_name, 
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_name = t.table_name) as column_count
FROM information_schema.tables t
WHERE table_schema = 'public' 
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- Check fraud_alerts columns
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'fraud_alerts'
ORDER BY ordinal_position;

-- Check suspicious_transactions columns
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'suspicious_transactions'
ORDER BY ordinal_position;