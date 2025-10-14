# Platform Initialization Scripts

Run scripts in order during first deployment.

## Usage

1. Copy config template:
```bash
   cp config.env.example config.env
```

2. Edit `config.env` with your values

3. Run scripts in order:
```bash
   ./1-kafka-topics.sh
```

## Requirements

- `kafka-topics.sh` in PATH (Kafka CLI tools)
- Network access to Kafka cluster