resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.project_name}-msk"
  kafka_version          = "3.6.0"
  
  # DEV: 1 broker, t3.small (~$50/month)
  number_of_broker_nodes = 1
  # DEMO: 3 brokers, m7g.large (~$600/month)
  # number_of_broker_nodes = 3
  
  broker_node_group_info {
    # DEV
    instance_type = "kafka.t3.small"
    # DEMO
    # instance_type = "kafka.m7g.large"
    
    # DEV
    client_subnets = [aws_subnet.private.id]
    # DEMO
    # client_subnets = [aws_subnet.private.id, aws_subnet.private_b.id, aws_subnet.private_c.id]
    
    storage_info {
      ebs_storage_info {
        volume_size = 100  # DEV: 100GB | DEMO: 250GB
      }
    }
    
    security_groups = [aws_security_group.msk.id]
  }
  
  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
  
  tags = {
    Name = "${var.project_name}-msk"
  }
}