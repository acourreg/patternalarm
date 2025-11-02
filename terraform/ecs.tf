# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"
  
  setting {
    name  = "containerInsights"
    value = "disabled"  # Économie en dev
  }
  
  tags = {
    Name = "${var.project_name}-ecs-cluster"
  }
}

# CloudWatch Log Group pour ECS
resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${var.project_name}"
  retention_in_days = 7
  
  tags = {
    Name = "${var.project_name}-ecs-logs"
  }
}

# ============================================================================
# CONFIGURATION DES SERVICES ECS
# ============================================================================

locals {
  ecs_services = {
    api-gateway = {
      cpu           = 256   # 0.25 vCPU - API REST simple
      memory        = 512   # 0.5 GB - Spring Boot minimal
      port          = 8080
      desired_count = 1
      is_bastion    = false
    }
    flink-processor = {
      cpu           = 512   # 0.5 vCPU - Flink avec state
      memory        = 1024  # 1 GB - JVM + Flink runtime
      port          = 8081
      desired_count = 1
      is_bastion    = false
    }
    dashboard = {
      cpu           = 256   # 0.25 vCPU - UI statique
      memory        = 512   # 0.5 GB - Spring Boot minimal
      port          = 8080
      desired_count = 1
      is_bastion    = false
    }
    # ✅ BASTION SSH - Always ON for RDS tunneling
    bastion = {
      cpu           = 256   # 0.25 vCPU - Ultra minimal
      memory        = 512   # 0.5 GB - Alpine + OpenSSH
      port          = 2222  # SSH port
      desired_count = 1     # Always running (~$7/month)
      is_bastion    = true
    }
  }
}

# ============================================================================
# TASK DEFINITIONS
# ============================================================================

resource "aws_ecs_task_definition" "services" {
  for_each = local.ecs_services
  
  family                   = "${var.project_name}-${each.key}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn
  
  container_definitions = each.value.is_bastion ? jsonencode([{
    # ✅ BASTION CONFIGURATION
    name  = "bastion"
    image = "linuxserver/openssh-server:latest"
    
    essential = true
    
    portMappings = [{
      containerPort = 2222
      protocol      = "tcp"
    }]
    
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "bastion"
      }
    }
    
    environment = [
      { name = "PUID", value = "1000" },
      { name = "PGID", value = "1000" },
      { name = "TZ", value = "America/Toronto" },
      { name = "USER_NAME", value = "ec2-user" },
      { name = "SUDO_ACCESS", value = "false" },
      { name = "PASSWORD_ACCESS", value = "false" },
      { name = "DOCKER_MODS", value = "linuxserver/mods:openssh-server-ssh-tunnel" }
    ]
    
    secrets = [
      {
        name      = "PUBLIC_KEY"
        valueFrom = aws_ssm_parameter.bastion_public_key.arn
      }
    ]
    
    healthCheck = {
      command     = ["CMD-SHELL", "nc -z localhost 2222 || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 30
    }
  }]) : jsonencode([{
    # ✅ STANDARD SERVICE CONFIGURATION
    name  = each.key
    image = "${aws_ecr_repository.main.repository_url}:${each.key}-latest"
    
    essential = true
    
    portMappings = [{
      containerPort = each.value.port
      protocol      = "tcp"
    }]
    
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = each.key
      }
    }
    
    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "ENVIRONMENT", value = "dev" },
      { name = "PORT", value = tostring(each.value.port) },
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/patternalarm" },
      { name = "DB_USER", value = "dbadmin" },
      { name = "DB_HOST", value = aws_db_instance.main.address },
      { name = "DB_PORT", value = tostring(aws_db_instance.main.port) },
      { name = "DB_NAME", value = "patternalarm" },
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.main.bootstrap_brokers },
      { name = "API_GATEWAY_URL", value = "http://api-gateway.${var.project_name}.local:8080" },
      # ✅ Redis connection
      { name = "REDIS_HOST", value = aws_elasticache_cluster.redis.cache_nodes[0].address },
      { name = "REDIS_PORT", value = tostring(aws_elasticache_cluster.redis.cache_nodes[0].port) }
    ]
    
    secrets = concat(
      each.key != "dashboard" ? [
        {
          name      = "DB_PASSWORD"
          valueFrom = aws_ssm_parameter.db_password.arn
        }
      ] : [],
      var.redis_password != "" ? [
        {
          name      = "REDIS_PASSWORD"
          valueFrom = aws_ssm_parameter.redis_password[0].arn
        }
      ] : []
    )
    
    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:${each.value.port}/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])
  
  tags = {
    Name    = "${var.project_name}-${each.key}-task"
    Service = each.key
  }
}

# ============================================================================
# SECURITY GROUPS
# ============================================================================

# Security Group pour ECS Tasks (services normaux)
resource "aws_security_group" "ecs_tasks" {
  name        = "${var.project_name}-ecs-tasks-sg"
  description = "Security group for ECS tasks"
  vpc_id      = aws_vpc.main.id
  
  ingress {
    description = "Allow inter-service communication"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }
  
  egress {
    description     = "Flink to MSK"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [aws_security_group.msk.id]
  }
  
  egress {
    description     = "Services to RDS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.rds.id]
  }
  
  egress {
    description = "HTTPS for ECR and AWS services"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  egress {
    description = "HTTP outbound"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = {
    Name = "${var.project_name}-ecs-tasks-sg"
  }
}

# ✅ Security Group pour le Bastion
resource "aws_security_group" "bastion" {
  name        = "${var.project_name}-bastion-sg"
  description = "Security group for SSH bastion (Fargate)"
  vpc_id      = aws_vpc.main.id
  
  # SSH depuis n'importe où (tu peux restreindre à ton IP)
  ingress {
    description = "SSH from anywhere (consider restricting to your IP)"
    from_port   = 2222
    to_port     = 2222
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]  # ⚠️ Change to ["YOUR_IP/32"] for better security
  }
  
  # Bastion → RDS
  egress {
    description     = "Bastion to RDS for tunneling"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.rds.id]
  }
  
  # HTTPS pour AWS APIs
  egress {
    description = "HTTPS for AWS services"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = {
    Name = "${var.project_name}-bastion-sg"
  }
}

# ============================================================================
# SECURITY GROUP RULES
# ============================================================================

resource "aws_security_group_rule" "msk_from_ecs" {
  type                     = "ingress"
  from_port                = 9092
  to_port                  = 9092
  protocol                 = "tcp"
  security_group_id        = aws_security_group.msk.id
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow Flink ECS tasks to access MSK"
}

resource "aws_security_group_rule" "rds_from_ecs" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.rds.id
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow ECS tasks to access RDS"
}

# ✅ Autoriser le bastion à accéder à RDS
resource "aws_security_group_rule" "rds_from_bastion" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.rds.id
  source_security_group_id = aws_security_group.bastion.id
  description              = "Allow Bastion to access RDS for SSH tunneling"
}

resource "aws_security_group_rule" "ecs_to_ecs_8080" {
  type                     = "egress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = aws_security_group.ecs_tasks.id
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow ECS tasks to call each other on port 8080"
}

# ============================================================================
# ECS SERVICES
# ============================================================================

resource "aws_ecs_service" "services" {
  for_each = local.ecs_services
  
  name            = "${var.project_name}-${each.key}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.services[each.key].arn
  desired_count   = each.value.desired_count
  launch_type     = "FARGATE"

  enable_execute_command = true
  
  network_configuration {
    # ✅ Bastion dans subnet PUBLIC, autres dans PRIVATE
    subnets = each.value.is_bastion ? [aws_subnet.public.id] : [aws_subnet.private.id]
    
    # ✅ Security group spécifique pour bastion
    security_groups = each.value.is_bastion ? [aws_security_group.bastion.id] : [aws_security_group.ecs_tasks.id]
    
    # ✅ IP publique uniquement pour bastion
    assign_public_ip = each.value.is_bastion ? true : false
  }
  
  dynamic "load_balancer" {
    for_each = each.key == "dashboard" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.dashboard.arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }
  
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100
  
  # Service discovery (pas pour bastion)
  dynamic "service_registries" {
    for_each = !each.value.is_bastion ? [1] : []
    content {
      registry_arn = aws_service_discovery_service.services[each.key].arn
    }
  }
  
  tags = {
    Name    = "${var.project_name}-${each.key}-service"
    Service = each.key
  }
  
  depends_on = [
    aws_iam_role_policy.ecs_task,
    aws_cloudwatch_log_group.ecs
  ]
}

# ============================================================================
# SERVICE DISCOVERY
# ============================================================================

resource "aws_service_discovery_private_dns_namespace" "main" {
  name = "${var.project_name}.local"
  vpc  = aws_vpc.main.id
  
  tags = {
    Name = "${var.project_name}-service-discovery"
  }
}

# Service Discovery Services (pas pour bastion)
resource "aws_service_discovery_service" "services" {
  for_each = { for k, v in local.ecs_services : k => v if !v.is_bastion }
  
  name = each.key
  
  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id
    
    dns_records {
      ttl  = 10
      type = "A"
    }
    
    routing_policy = "MULTIVALUE"
  }
  
  tags = {
    Name    = "${var.project_name}-${each.key}-discovery"
    Service = each.key
  }
}

# ============================================================================
# OUTPUTS
# ============================================================================

output "ecs_cluster_name" {
  value       = aws_ecs_cluster.main.name
  description = "ECS cluster name"
}

output "ecs_cluster_arn" {
  value       = aws_ecs_cluster.main.arn
  description = "ECS cluster ARN"
}

output "ecs_services" {
  value = {
    for service_name, service in aws_ecs_service.services :
    service_name => {
      name          = service.name
      desired_count = service.desired_count
      task_def      = aws_ecs_task_definition.services[service_name].arn
    }
  }
  description = "ECS services deployed"
}

output "service_discovery_namespace" {
  value       = aws_service_discovery_private_dns_namespace.main.name
  description = "Service discovery namespace for inter-service communication"
}

# ✅ Bastion connection info
output "bastion_connection_info" {
  value = <<-EOT
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    🔐 BASTION SSH ACCESS
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    Status: Always Running (desired_count=1)
    Private Key: ${local_file.bastion_private_key.filename}
    
    1️⃣ Get bastion IP:
       TASK_ARN=$(aws ecs list-tasks --cluster ${aws_ecs_cluster.main.name} --service-name ${aws_ecs_service.services["bastion"].name} --query 'taskArns[0]' --output text)
       ENI_ID=$(aws ecs describe-tasks --cluster ${aws_ecs_cluster.main.name} --tasks $TASK_ARN --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
       BASTION_IP=$(aws ec2 describe-network-interfaces --network-interface-ids $ENI_ID --query 'NetworkInterfaces[0].Association.PublicIp' --output text)
       echo "Bastion IP: $BASTION_IP"
       
    2️⃣ Create SSH tunnel:
       ssh -i ${local_file.bastion_private_key.filename} -N -L 5433:${aws_db_instance.main.address}:5432 ec2-user@$BASTION_IP -p 2222
       
    3️⃣ Connect to database (in another terminal):
       psql -h localhost -p 5433 -U dbadmin -d patternalarm
    
    📝 Security: Bastion accepts SSH from 0.0.0.0/0
       Consider restricting to your IP in aws_security_group.bastion
    
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  EOT
  description = "Bastion connection instructions"
}

output "bastion_private_key_path" {
  value       = local_file.bastion_private_key.filename
  description = "Path to bastion SSH private key"
}

output "ecs_task_costs_estimate" {
  value = {
    api_gateway     = "~$4.50/month (0.25 vCPU, 0.5GB)"
    flink_processor = "~$9/month (0.5 vCPU, 1GB)"
    dashboard       = "~$4.50/month (0.25 vCPU, 0.5GB)"
    bastion         = "~$7/month (0.25 vCPU, 0.5GB) - Always ON"
    total           = "~$25/month for all services 24/7"
  }
  description = "Estimated monthly costs for ECS services"
}
