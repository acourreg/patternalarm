# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"
  
  setting {
    name  = "containerInsights"
    value = "disabled"
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
      cpu           = 256
      memory        = 1024
      port          = 8080
      desired_count = 1
      is_bastion    = false
      is_airflow    = false
    }
    flink-processor = {
      cpu           = 512
      memory        = 1024
      port          = 8081
      desired_count = 1
      is_bastion    = false
      is_airflow    = false
    }
    dashboard = {
      cpu           = 256
      memory        = 512
      port          = 8080
      desired_count = 1
      is_bastion    = false
      is_airflow    = false
    }
    bastion = {
      cpu           = 256
      memory        = 512
      port          = 2222
      desired_count = 1
      is_bastion    = true
      is_airflow    = false
    }
    airflow = {
      cpu           = 2048   # 2 vCPU
      memory        = 4096   # 4 GB
      port          = 8080
      desired_count = 1
      is_bastion    = false
      is_airflow    = true
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
  task_role_arn            = each.value.is_airflow ? aws_iam_role.airflow_task.arn : aws_iam_role.ecs_task.arn

  container_definitions = each.value.is_bastion ? jsonencode([{
    # ✅ BASTION CONFIGURATION
    name      = "bastion"
    image     = "linuxserver/openssh-server:latest"
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

    secrets = [{
      name      = "PUBLIC_KEY"
      valueFrom = aws_ssm_parameter.bastion_public_key.arn
    }]

    healthCheck = {
      command     = ["CMD-SHELL", "nc -z localhost 2222 || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 30
    }
  }]) : each.value.is_airflow ? jsonencode([{
    # ✅ AIRFLOW CONFIGURATION
    name      = "airflow"
    image     = "${aws_ecr_repository.main.repository_url}:airflow-latest"
    essential = true

    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "airflow"
      }
    }

    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "ENVIRONMENT", value = "prod" },
      { name = "S3_BUCKET", value = aws_s3_bucket.data.id },
      { name = "EMR_APPLICATION_ID", value = aws_emrserverless_application.spark.id },
      { name = "EMR_JOB_ROLE_ARN", value = aws_iam_role.emr_job.arn },
      { name = "AIRFLOW_ADMIN_USERNAME", value = "admin" },
      { name = "AIRFLOW_ADMIN_PASSWORD", value = "Xk9mP2vL4nQz" }
    ]

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 120
    }
  }]) : jsonencode([{
    # ✅ STANDARD SERVICE CONFIGURATION
    name      = each.key
    image     = "${aws_ecr_repository.main.repository_url}:${each.key}-latest"
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
      { name = "REDIS_HOST", value = aws_elasticache_cluster.redis.cache_nodes[0].address },
      { name = "REDIS_PORT", value = tostring(aws_elasticache_cluster.redis.cache_nodes[0].port) },
      { name = "MODEL_PATH", value = "s3://${aws_s3_bucket.data.id}/models/fraud_detector_v1" }
    ]

    secrets = concat(
      each.key != "dashboard" ? [{
        name      = "DB_PASSWORD"
        valueFrom = aws_ssm_parameter.db_password.arn
      }] : [],
      var.redis_password != "" ? [{
        name      = "REDIS_PASSWORD"
        valueFrom = aws_ssm_parameter.redis_password[0].arn
      }] : []
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
# AIRFLOW IAM ROLE
# ============================================================================

resource "aws_iam_role" "airflow_task" {
  name = "${var.project_name}-airflow-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "airflow_task" {
  name = "${var.project_name}-airflow-policy"
  role = aws_iam_role.airflow_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = [
          "emr-serverless:StartJobRun",
          "emr-serverless:GetJobRun",
          "emr-serverless:CancelJobRun",
          "emr-serverless:GetApplication",
          "emr-serverless:ListJobRuns",
          "emr-serverless:CreateApplication",
          "emr-serverless:GetApplication",
          "emr-serverless:ListApplications",
          "emr-serverless:StartApplication",
          "emr-serverless:StopApplication",
          "emr-serverless:DeleteApplication",
          "emr-serverless:StartJobRun",
          "emr-serverless:GetJobRun",
          "emr-serverless:ListJobRuns",
          "emr-serverless:CancelJobRun",
          "emr-serverless:TagResource"
        ]
        Resource = [aws_emrserverless_application.spark.arn, "${aws_emrserverless_application.spark.arn}/*"]
      },
      {
        Effect   = "Allow"
        Action   = "iam:PassRole"
        Resource = aws_iam_role.emr_job.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:ListBucket"]
        Resource = [aws_s3_bucket.data.arn, "${aws_s3_bucket.data.arn}/*"]
      }
    ]
  })
}

# ============================================================================
# SECURITY GROUPS
# ============================================================================

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

resource "aws_security_group" "bastion" {
  name        = "${var.project_name}-bastion-sg"
  description = "Security group for SSH bastion (Fargate)"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH from anywhere"
    from_port   = 2222
    to_port     = 2222
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description     = "Bastion to RDS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.rds.id]
  }

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

resource "aws_security_group" "airflow" {
  name        = "${var.project_name}-airflow-sg"
  description = "Security group for Airflow"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from anywhere"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-airflow-sg"
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

resource "aws_security_group_rule" "rds_from_bastion" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.rds.id
  source_security_group_id = aws_security_group.bastion.id
  description              = "Allow Bastion to access RDS"
}

resource "aws_security_group_rule" "ecs_to_ecs_8080" {
  type                     = "egress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = aws_security_group.ecs_tasks.id
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow ECS tasks to call each other"
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
    subnets = (each.value.is_bastion || each.value.is_airflow) ? [aws_subnet.public.id] : [aws_subnet.private.id]

    security_groups = each.value.is_bastion ? [aws_security_group.bastion.id] : (
      each.value.is_airflow ? [aws_security_group.airflow.id] : [aws_security_group.ecs_tasks.id]
    )

    assign_public_ip = (each.value.is_bastion || each.value.is_airflow) ? true : false
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

  dynamic "service_registries" {
    for_each = (!each.value.is_bastion && !each.value.is_airflow) ? [1] : []
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

resource "aws_service_discovery_service" "services" {
  for_each = { for k, v in local.ecs_services : k => v if !v.is_bastion && !v.is_airflow }

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
  description = "Service discovery namespace"
}

output "bastion_connection_info" {
  value = <<-EOT
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    🔐 BASTION SSH ACCESS
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    Private Key: ${local_file.bastion_private_key.filename}

    1️⃣ Get bastion IP:
       TASK_ARN=$(aws ecs list-tasks --cluster ${aws_ecs_cluster.main.name} --service-name ${var.project_name}-bastion --query 'taskArns[0]' --output text)
       ENI_ID=$(aws ecs describe-tasks --cluster ${aws_ecs_cluster.main.name} --tasks $TASK_ARN --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
       BASTION_IP=$(aws ec2 describe-network-interfaces --network-interface-ids $ENI_ID --query 'NetworkInterfaces[0].Association.PublicIp' --output text)

    2️⃣ SSH tunnel: ssh -i ${local_file.bastion_private_key.filename} -N -L 5433:${aws_db_instance.main.address}:5432 ec2-user@$BASTION_IP -p 2222
    3️⃣ Connect: psql -h localhost -p 5433 -U dbadmin -d patternalarm
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  EOT
  description = "Bastion connection instructions"
}

output "bastion_private_key_path" {
  value       = local_file.bastion_private_key.filename
  description = "Path to bastion SSH private key"
}

output "airflow_credentials" {
  value = {
    username = "admin"
    password = "Xk9mP2vL4nQz"
  }
  sensitive   = true
  description = "Airflow admin credentials"
}

output "ecs_task_costs_estimate" {
  value = {
    api_gateway     = "~$4.50/month (0.25 vCPU, 0.5GB)"
    flink_processor = "~$9/month (0.5 vCPU, 1GB)"
    dashboard       = "~$4.50/month (0.25 vCPU, 0.5GB)"
    bastion         = "~$7/month (0.25 vCPU, 0.5GB)"
    airflow         = "~$18/month (1 vCPU, 2GB)"
    total           = "~$43/month for all services 24/7"
  }
  description = "Estimated monthly costs"
}
