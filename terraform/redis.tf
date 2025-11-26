# ============================================================================
# REDIS ELASTICACHE
# ============================================================================

# Redis subnet group
resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.project_name}-redis-subnet"
  subnet_ids = [aws_subnet.private.id, aws_subnet.private_b.id]

  tags = {
    Name = "${var.project_name}-redis-subnet-group"
  }
}

# Redis security group
resource "aws_security_group" "redis" {
  name        = "${var.project_name}-redis-sg"
  description = "Security group for Redis ElastiCache"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-redis-sg"
  }
}

# Allow ECS tasks to access Redis
resource "aws_security_group_rule" "redis_from_ecs" {
  type                     = "ingress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  security_group_id        = aws_security_group.redis.id
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow ECS tasks to access Redis"
}

# Redis cluster
resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "${var.project_name}-redis"
  engine               = "redis"
  node_type            = "cache.t3.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  engine_version       = "7.1"
  port                 = 6379
  
  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]
  
  tags = {
    Name = "${var.project_name}-redis"
  }
}

# Store Redis password in SSM (if auth enabled)
resource "aws_ssm_parameter" "redis_password" {
  count = var.redis_password != "" ? 1 : 0
  
  name        = "/patternalarm/redis-password"
  description = "Redis AUTH password"
  type        = "SecureString"
  value       = var.redis_password

  tags = {
    Name = "${var.project_name}-redis-password"
  }
}

# ============================================================================
# OUTPUTS
# ============================================================================

output "redis_endpoint" {
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
  description = "Redis primary endpoint (hostname only)"
}

output "redis_port" {
  value       = aws_elasticache_cluster.redis.cache_nodes[0].port
  description = "Redis port"
}

output "redis_url" {
  value = var.redis_password != "" ? (
    "redis://:${var.redis_password}@${aws_elasticache_cluster.redis.cache_nodes[0].address}:${aws_elasticache_cluster.redis.cache_nodes[0].port}"
  ) : (
    "redis://${aws_elasticache_cluster.redis.cache_nodes[0].address}:${aws_elasticache_cluster.redis.cache_nodes[0].port}"
  )
  description = "Complete Redis connection URL"
  sensitive   = true
}

output "redis_connection_info" {
  value = <<-EOT
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    🔴 REDIS ELASTICACHE
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    Endpoint: ${aws_elasticache_cluster.redis.cache_nodes[0].address}
    Port: ${aws_elasticache_cluster.redis.cache_nodes[0].port}
    Engine: Redis ${aws_elasticache_cluster.redis.engine_version}
    Node Type: ${aws_elasticache_cluster.redis.node_type}
    Auth: ${var.redis_password != "" ? "Enabled" : "Disabled"}
    
    Environment Variables:
    export REDIS_URL="${var.redis_password != "" ? "redis://:****@" : "redis://"}${aws_elasticache_cluster.redis.cache_nodes[0].address}:${aws_elasticache_cluster.redis.cache_nodes[0].port}"
    export REDIS_HOST="${aws_elasticache_cluster.redis.cache_nodes[0].address}"
    export REDIS_PORT="${aws_elasticache_cluster.redis.cache_nodes[0].port}"
    ${var.redis_password != "" ? "export REDIS_PASSWORD=\"****\"" : "# No password required"}
    
    Cost: ~$12/month (cache.t3.micro, 24/7)
    
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  EOT
  description = "Redis connection information"
}