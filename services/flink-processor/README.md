
---

## 🧪 Local Testing

### Query Kafka Messages
```bash

docker exec -it kafka /usr/bin/kafka-console-consumer \
--bootstrap-server localhost:9092 \
--topic gaming-events \
--from-beginning \
--max-messages 5
```


## 📥 Kafka Topics & Message Formats

### **Topic: `gaming-events`**

**Example Message:**
```json
{
  "player_id": "A828424",
  "game_id": "FortniteClone",
  "item_type": "currency",
  "item_name": "Gold_313",
  "amount": 2.75,
  "currency": "USD",
  "payment_method": "credit_card",
  "ip_address": "212.116.153.116",
  "device_id": "P2OMECENEDQU",
  "session_length_sec": 20,
  "transaction_id": "SZQGXEJR3YCZVVW1",
  "domain": "gaming",
  "test_id": "test-integration",
  "timestamp": "2025-10-15T22:30:58.914680+00:00",
  "pattern": "fraud_gold_farming",
  "is_fraud": true,
  "actor_id": "A828424",
  "sequence_position": 0
}
```

**Domain-Specific Fields:**
- `player_id`, `game_id`, `item_type`, `item_name`, `session_length_sec`

---

### **Topic: `fintech-transactions`**

**Example Message:**
```json
{
  "account_from": "ACC8234567",
  "account_to": "ACC9123456",
  "amount": 850.50,
  "currency": "USD",
  "transfer_type": "ach",
  "country_from": "US",
  "country_to": "US",
  "purpose": "payment",
  "ip_address": "192.168.1.45",
  "transaction_id": "TXN7G2K9P4L3M8N1",
  "domain": "fintech",
  "test_id": "test-integration",
  "timestamp": "2025-10-15T22:31:10.123456+00:00",
  "pattern": "regular_bill_payer",
  "is_fraud": false,
  "actor_id": "ACC8234567",
  "sequence_position": 0
}
```

**Domain-Specific Fields:**
- `account_from`, `account_to`, `transfer_type`, `country_from`, `country_to`, `purpose`

---

### **Topic: `ecommerce-orders`**

**Example Message:**
```json
{
  "user_id": "U456789",
  "cart_items": [
    {"product_id": "PROD123", "quantity": 2, "price": 49.99},
    {"product_id": "PROD456", "quantity": 1, "price": 29.99}
  ],
  "amount": 129.97,
  "currency": "USD",
  "payment_method": "credit_card",
  "shipping_address": "123 Main St, New York, NY",
  "billing_address": "123 Main St, New York, NY",
  "ip_address": "203.45.67.89",
  "device_fingerprint": "FP8K3L9M2N4P5Q",
  "session_duration_sec": 1200,
  "transaction_id": "ORD5H7J9K2L4M6N8",
  "domain": "ecommerce",
  "test_id": "test-integration",
  "timestamp": "2025-10-15T22:32:15.987654+00:00",
  "pattern": "regular_shopper",
  "is_fraud": false,
  "actor_id": "U456789",
  "sequence_position": 0
}
```
