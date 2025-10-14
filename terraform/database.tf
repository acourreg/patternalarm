resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet"
  
  # APRÈS (FIXED):
  subnet_ids = [
    aws_subnet.private.id,
    aws_subnet.private_b.id
  ]
  
  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

# Reste identique

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