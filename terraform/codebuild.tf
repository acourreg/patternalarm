# CloudWatch Log Group unique pour tous les CodeBuild
resource "aws_cloudwatch_log_group" "codebuild" {
  name              = "/aws/codebuild/${var.project_name}"
  retention_in_days = 7

  tags = {
    Name = "${var.project_name}-codebuild-logs"
  }
}

# Configuration des services avec leurs spécificités
locals {
  services = {
    api-gateway = {
      compute_type = "BUILD_GENERAL1_SMALL"
      buildspec    = "services/api-gateway/buildspec.yml"
      description  = "Build Docker image for API Gateway and push to ECR"
    }
    flink-processor = {
      compute_type = "BUILD_GENERAL1_MEDIUM"
      buildspec    = "services/flink-processor/buildspec.yml"
      description  = "Build Docker image for Flink Processor and push to ECR"
    }
    dashboard = {
      compute_type = "BUILD_GENERAL1_SMALL"
      buildspec    = "services/dashboard/buildspec.yml"
      description  = "Build Docker image for Dashboard and push to ECR"
    }
    event-simulator = {
      compute_type = "BUILD_GENERAL1_SMALL"
      buildspec    = "services/event-simulator/buildspec.yml"
      description  = "Package Lambda functions and deploy to AWS Lambda"
    }
    airflow = {
      compute_type = "BUILD_GENERAL1_MEDIUM"
      buildspec    = "services/airflow/buildspec.yml"
      description  = "Build Airflow image, push to ECR, upload Spark jobs to S3"
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
      },
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.data.arn,
          "${aws_s3_bucket.data.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "ecs:UpdateService",
          "ecs:DescribeServices"
        ]
        Resource = "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${var.project_name}-cluster/*"
      }
    ]
  })
}

# CodeBuild Projects
resource "aws_codebuild_project" "services" {
  for_each = local.services

  name          = "${var.project_name}-${each.key}"
  description   = each.value.description
  service_role  = aws_iam_role.codebuild.arn
  build_timeout = 30

  artifacts {
    type = "NO_ARTIFACTS"
  }

  environment {
    compute_type                = each.value.compute_type
    image                       = "aws/codebuild/standard:7.0"
    type                        = "LINUX_CONTAINER"
    privileged_mode             = true
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

    environment_variable {
      name  = "S3_BUCKET"
      value = aws_s3_bucket.data.id
    }
  }

  logs_config {
    cloudwatch_logs {
      group_name  = aws_cloudwatch_log_group.codebuild.name
      stream_name = each.key
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
