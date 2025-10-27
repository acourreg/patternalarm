# CloudWatch Log Group unique pour tous les CodeBuild
# Les log streams seront créés automatiquement par build
resource "aws_cloudwatch_log_group" "codebuild" {
  name              = "/aws/codebuild/${var.project_name}"
  retention_in_days = 7  # Dev mode - pas besoin de garder longtemps
  
  tags = {
    Name = "${var.project_name}-codebuild-logs"
  }
}

# Configuration des services avec leurs spécificités
locals {
  services = {
    api-gateway = {
      compute_type = "BUILD_GENERAL1_SMALL"   # 3GB RAM, 2 vCPU
      buildspec    = "services/api-gateway/buildspec.yml"
      description  = "Build Docker image for API Gateway and push to ECR"
    }
    flink-processor = {
      compute_type = "BUILD_GENERAL1_MEDIUM"  # 7GB RAM, 4 vCPU (Scala needs more)
      buildspec    = "services/flink-processor/buildspec.yml"
      description  = "Build Docker image for Flink Processor and push to ECR"
    }
    dashboard = {
      compute_type = "BUILD_GENERAL1_SMALL"   # 3GB RAM, 2 vCPU
      buildspec    = "services/dashboard/buildspec.yml"
      description  = "Build Docker image for Dashboard and push to ECR"
    }
    event-simulator = {
      compute_type = "BUILD_GENERAL1_SMALL"   # 3GB RAM, 2 vCPU
      buildspec    = "services/event-simulator/buildspec.yml"
      description  = "Package Lambda functions and deploy to AWS Lambda"
    }
  }
}

# IAM Role for CodeBuild
resource "aws_iam_role" "codebuild" {
  name = "${var.project_name}-codebuild-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "codebuild.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
  
  tags = {
    Name = "${var.project_name}-codebuild-role"
  }
}

# IAM Policy for CodeBuild
resource "aws_iam_role_policy" "codebuild" {
  name = "${var.project_name}-codebuild-policy"
  role = aws_iam_role.codebuild.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "lambda:UpdateFunctionCode",
          "lambda:GetFunction",
          "lambda:PublishVersion"
        ]
        Resource = "arn:aws:lambda:${var.aws_region}:${data.aws_caller_identity.current.account_id}:function:${var.project_name}-*"
      }
    ]
  })
}


# CodeBuild Projects - 1 resource avec for_each pour DRY code
resource "aws_codebuild_project" "services" {
  for_each = local.services
  
  name          = "${var.project_name}-${each.key}"
  description   = each.value.description
  service_role  = aws_iam_role.codebuild.arn
  build_timeout = 30  # minutes
  
  artifacts {
    type = "NO_ARTIFACTS"
  }
  
  environment {
    compute_type                = each.value.compute_type
    image                       = "aws/codebuild/standard:7.0"
    type                        = "LINUX_CONTAINER"
    privileged_mode             = true  # Required for Docker builds
    image_pull_credentials_type = "CODEBUILD"
    
    environment_variable {
      name  = "AWS_DEFAULT_REGION"
      value = var.aws_region
    }
    
    environment_variable {
      name  = "AWS_ACCOUNT_ID"
      value = data.aws_caller_identity.current.account_id
    }
    
    environment_variable {
      name  = "IMAGE_REPO_NAME"
      value = aws_ecr_repository.main.name
    }
    
    environment_variable {
      name  = "SERVICE_NAME"
      value = each.key
    }
    
    environment_variable {
      name  = "PROJECT_NAME"
      value = var.project_name
    }
  }
  
  logs_config {
    cloudwatch_logs {
      group_name  = aws_cloudwatch_log_group.codebuild.name
      stream_name = each.key  # Log stream par service
    }
  }
  
  source {
    type            = "GITHUB"
    location        = "https://github.com/acourreg/patternalarm.git"
    git_clone_depth = 1
    buildspec       = each.value.buildspec
    
    git_submodules_config {
      fetch_submodules = false
    }
  }
  
  tags = {
    Name    = "${var.project_name}-${each.key}-build"
    Service = each.key
  }
}

# Data source pour récupérer l'account ID
data "aws_caller_identity" "current" {}

# Outputs
output "codebuild_projects" {
  value = {
    for service_name, project in aws_codebuild_project.services :
    service_name => project.name
  }
  description = "Map of service names to CodeBuild project names"
}

output "codebuild_log_group" {
  value       = aws_cloudwatch_log_group.codebuild.name
  description = "CloudWatch log group for all CodeBuild projects"
}
