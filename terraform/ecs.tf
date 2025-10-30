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

# Configuration des services ECS avec sizing minimal
locals {
  ecs_services = {
    api-gateway = {
      cpu           = 256   # 0.25 vCPU - API REST simple
      memory        = 512   # 0.5 GB - Spring Boot minimal
      port          = 8080
      desired_count = 1
    }
    flink-processor = {
      cpu           = 512   # 0.5 vCPU - Flink avec state
      memory        = 1024  # 1 GB - JVM + Flink runtime
      port          = 8081
      desired_count = 1
    }
    dashboard = {
      cpu           = 256   # 0.25 vCPU - UI statique
      memory        = 512   # 0.5 GB - Spring Boot minimal
      port          = 8080
      desired_count = 1
    }
  }
}

# Task Definitions
resource "aws_ecs_task_definition" "services" {
  for_each = local.ecs_services
  
  family                   = "${var.project_name}-${each.key}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn
  
  
  container_definitions = jsonencode([{
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
    
    
    # Variables d'environnement communes
    # Variables d'environnement communes
    environment = concat(
      [
        { name = "AWS_REGION", value = var.aws_region },
        { name = "ENVIRONMENT", value = "dev" },
        { name = "PORT", value = tostring(each.value.port) }  # ✅ Add this - uses port from locals
      ],
      # Variables spécifiques par service
      each.key == "flink-processor" ? [
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.main.bootstrap_brokers },
        { name = "DB_HOST", value = aws_db_instance.main.address },
        { name = "DB_PORT", value = tostring(aws_db_instance.main.port) },
        { name = "DB_NAME", value = "patternalarm" },
        { name = "API_GATEWAY_URL", value = "http://api-gateway.${var.project_name}.local:8080" }
      ] : each.key == "api-gateway" ? [
        { name = "DB_HOST", value = aws_db_instance.main.address },
        { name = "DB_PORT", value = tostring(aws_db_instance.main.port) },
        { name = "DB_NAME", value = "patternalarm" }
      ] : each.key == "dashboard" ? [
        { name = "API_GATEWAY_URL", value = "http://api-gateway.${var.project_name}.local:8080" }
      ] : []
    )
    
    # Secrets depuis Parameter Store (sauf dashboard)
    secrets = each.key != "dashboard" ? [
      {
        name      = "DB_PASSWORD"
        valueFrom = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/patternalarm/db-password"
      }
    ] : []
    
    # Health check basique
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

# Security Group pour ECS Tasks
resource "aws_security_group" "ecs_tasks" {
  name        = "${var.project_name}-ecs-tasks-sg"
  description = "Security group for ECS tasks"
  vpc_id      = aws_vpc.main.id
  
  # Allow inter-service communication
  ingress {
    description = "Allow inter-service communication"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }
  
  # Flink → MSK
  egress {
    description     = "Flink to MSK"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [aws_security_group.msk.id]
  }
  
  # API + Flink → RDS
  egress {
    description     = "Services to RDS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.rds.id]
  }
  
  # HTTPS pour ECR pulls + AWS APIs
  egress {
    description = "HTTPS for ECR and AWS services"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  # HTTP pour sanity checks
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

# Mettre à jour le Security Group MSK pour accepter traffic depuis ECS
resource "aws_security_group_rule" "msk_from_ecs" {
  type                     = "ingress"
  from_port                = 9092
  to_port                  = 9092
  protocol                 = "tcp"
  security_group_id        = aws_security_group.msk.id
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow Flink ECS tasks to access MSK"
}

# Mettre à jour le Security Group RDS pour accepter traffic depuis ECS
resource "aws_security_group_rule" "rds_from_ecs" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.rds.id
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow ECS tasks to access RDS"
}

resource "aws_security_group_rule" "ecs_to_ecs_8080" {
  type              = "egress"
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"
  security_group_id = aws_security_group.ecs_tasks.id
  source_security_group_id = aws_security_group.ecs_tasks.id
  description       = "Allow ECS tasks to call each other on port 8080"
}

# ECS Services
resource "aws_ecs_service" "services" {
  for_each = local.ecs_services
  
  name            = "${var.project_name}-${each.key}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.services[each.key].arn
  desired_count   = each.value.desired_count
  launch_type     = "FARGATE"

  enable_execute_command = true
  
  network_configuration {
    subnets          = [aws_subnet.private.id]
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }
  
  dynamic "load_balancer" {
    for_each = each.key == "dashboard" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.dashboard.arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }
  
  # Restart policy
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100
  
  # Service discovery via Cloud Map (optionnel mais utile pour inter-service)
  service_registries {
    registry_arn = aws_service_discovery_service.services[each.key].arn
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

# Service Discovery Namespace
resource "aws_service_discovery_private_dns_namespace" "main" {
  name = "${var.project_name}.local"
  vpc  = aws_vpc.main.id
  
  tags = {
    Name = "${var.project_name}-service-discovery"
  }
}

# Service Discovery Services
resource "aws_service_discovery_service" "services" {
  for_each = local.ecs_services
  
  name = each.key
  
  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id
    
    dns_records {
      ttl  = 10
      type = "A"
    }
    
    routing_policy = "MULTIVALUE"
  }
  
  # ✅ No health check for private DNS - trust ECS health checks
  
  tags = {
    Name    = "${var.project_name}-${each.key}-discovery"
    Service = each.key
  }
}

# Outputs
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

output "ecs_task_costs_estimate" {
  value = {
    api_gateway      = "~$4.50/month (0.25 vCPU, 0.5GB)"
    flink_processor  = "~$9/month (0.5 vCPU, 1GB)"
    dashboard        = "~$4.50/month (0.25 vCPU, 0.5GB)"
    total            = "~$18/month for 24/7 runtime"
  }
  description = "Estimated monthly costs for ECS services"
}
