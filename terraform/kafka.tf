# MSK Configuration with auto-create topics enabled
resource "aws_msk_configuration" "main" {
  name              = "${var.project_name}-msk-config"
  kafka_versions    = ["3.6.0"]
  
  server_properties = <<PROPERTIES
auto.create.topics.enable=true
default.replication.factor=2
min.insync.replicas=1
num.partitions=3
log.retention.hours=168
PROPERTIES

  description = "MSK configuration with auto-create topics enabled"
}

# MSK Cluster
resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.project_name}-msk-${var.random_suffix}"
  kafka_version          = "3.6.0"
  
  # DEV: 2 brokers, t3.small (~$50/month)
  number_of_broker_nodes = 2
  # DEMO: 3 brokers, m7g.large (~$600/month)
  # number_of_broker_nodes = 3
  
  broker_node_group_info {
    # DEV
    instance_type = "kafka.t3.small"
    # DEMO
    # instance_type = "kafka.m7g.large"
    
    # DEV
    client_subnets = [
      aws_subnet.private.id,      # us-east-1a
      aws_subnet.private_b.id     # us-east-1b
    ]

    # DEMO
    # client_subnets = [
    #   aws_subnet.private.id,
    #   aws_subnet.private_b.id,
    #   aws_subnet.private_c.id
    # ]
    
    storage_info {
      ebs_storage_info {
        volume_size = 100  # DEV: 100GB | DEMO: 250GB
      }
    }
    
    security_groups = [aws_security_group.msk.id]
  }
  
  # Apply custom configuration
  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }
  
  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"  # ✅ Dev mode - no auth needed
      in_cluster    = true
    }
    # DEMO: Use TLS + SASL/SCRAM
    # encryption_in_transit {
    #   client_broker = "TLS"
    #   in_cluster    = true
    # }
  }
  
  tags = {
    Name = "${var.project_name}-msk"
  }
}

# Outputs
output "msk_bootstrap_brokers" {
  value       = aws_msk_cluster.main.bootstrap_brokers
  description = "MSK broker endpoints (plaintext)"
}

output "msk_cluster_arn" {
  value       = aws_msk_cluster.main.arn
  description = "MSK cluster ARN"
}
