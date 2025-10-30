resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet"
  
  subnet_ids = [
    aws_subnet.private.id,
    aws_subnet.private_b.id
  ]
  
  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

resource "aws_db_instance" "main" {
  identifier        = "${var.project_name}-db"
  engine            = "postgres"
  engine_version    = "15"
  instance_class    = "db.t3.small"
  allocated_storage = 20
  
  db_name  = "patternalarm"
  username = "dbadmin"
  password = var.db_password
  
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  
  skip_final_snapshot = true
  publicly_accessible = false
  
  tags = {
    Name = "${var.project_name}-rds"
  }
}

# Store DB password in SSM Parameter Store
resource "aws_ssm_parameter" "db_password" {
  name        = "/patternalarm/db-password"
  description = "RDS database password"
  type        = "SecureString"
  value       = var.db_password

  tags = {
    Name = "${var.project_name}-db-password"
  }
}

# ============================================================================
# OUTPUTS
# ============================================================================

output "rds_endpoint" {
  value       = aws_db_instance.main.endpoint
  description = "RDS endpoint for database connections"
}

output "rds_address" {
  value       = aws_db_instance.main.address
  description = "RDS address (hostname only)"
}

output "rds_port" {
  value       = aws_db_instance.main.port
  description = "RDS port"
}
