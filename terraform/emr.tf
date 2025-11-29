# ============================================================================
# EMR SERVERLESS APPLICATION
# ============================================================================

resource "aws_emrserverless_application" "spark" {
  name          = "${var.project_name}-spark"
  release_label = "emr-7.1.0"
  type          = "SPARK"

  initial_capacity {
    initial_capacity_type = "DRIVER"
    initial_capacity_config {
      worker_count = 1
      worker_configuration {
        cpu    = "2vCPU"
        memory = "4GB"
      }
    }
  }

  initial_capacity {
    initial_capacity_type = "EXECUTOR"
    initial_capacity_config {
      worker_count = 2
      worker_configuration {
        cpu    = "2vCPU"
        memory = "4GB"
      }
    }
  }

  maximum_capacity {
    cpu    = "16vCPU"
    memory = "32GB"
  }

  auto_start_configuration {
    enabled = true
  }

  auto_stop_configuration {
    enabled              = true
    idle_timeout_minutes = 5
  }

  network_configuration {
    subnet_ids         = [aws_subnet.private.id, aws_subnet.private_b.id]
    security_group_ids = [aws_security_group.emr.id]
  }

  tags = {
    Name = "${var.project_name}-emr-spark"
  }
}

# ============================================================================
# EMR SECURITY GROUP
# ============================================================================

resource "aws_security_group" "emr" {
  name        = "${var.project_name}-emr-sg"
  description = "Security group for EMR Serverless"
  vpc_id      = aws_vpc.main.id

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-emr-sg"
  }
}

# ============================================================================
# IAM ROLE FOR EMR JOB EXECUTION
# ============================================================================

resource "aws_iam_role" "emr_job" {
  name = "${var.project_name}-emr-job-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "emr-serverless.amazonaws.com"
      }
    }]
  })

  tags = {
    Name = "${var.project_name}-emr-job-role"
  }
}

resource "aws_iam_role_policy" "emr_job" {
  name = "${var.project_name}-emr-job-policy"
  role = aws_iam_role.emr_job.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "S3Access"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.data.arn,
          "${aws_s3_bucket.data.arn}/*"
        ]
      },
      {
        Sid    = "GlueAccess"
        Effect = "Allow"
        Action = [
          "glue:GetDatabase",
          "glue:GetDatabases",
          "glue:GetTable",
          "glue:GetTables",
          "glue:GetPartition",
          "glue:GetPartitions"
        ]
        Resource = ["*"]
      },
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = ["arn:aws:logs:${var.aws_region}:*:log-group:/aws/emr-serverless/*"]
      }
    ]
  })
}

# ============================================================================
# OUTPUTS
# ============================================================================

output "emr_application_id" {
  value       = aws_emrserverless_application.spark.id
  description = "EMR Serverless application ID"
}

output "emr_application_arn" {
  value       = aws_emrserverless_application.spark.arn
  description = "EMR Serverless application ARN"
}

output "emr_job_role_arn" {
  value       = aws_iam_role.emr_job.arn
  description = "IAM role ARN for EMR job execution"
}

output "emr_spark_submit_example" {
  value = <<-EOT
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    ⚡ EMR SERVERLESS SPARK
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    Application ID: ${aws_emrserverless_application.spark.id}
    Release: emr-7.1.0
    Auto-stop: 5 min idle
    
    Submit job:
    aws emr-serverless start-job-run \
      --application-id ${aws_emrserverless_application.spark.id} \
      --execution-role-arn ${aws_iam_role.emr_job.arn} \
      --job-driver '{
        "sparkSubmit": {
          "entryPoint": "s3://${aws_s3_bucket.data.id}/spark-jobs/train_model.py",
          "entryPointArguments": [
            "--features-path", "s3://${aws_s3_bucket.data.id}/data/features",
            "--model-output", "s3://${aws_s3_bucket.data.id}/models/fraud_detector_v1"
          ],
          "sparkSubmitParameters": "--py-files s3://${aws_s3_bucket.data.id}/libs/feature_store.zip"
        }
      }' \
      --configuration-overrides '{
        "monitoringConfiguration": {
          "s3MonitoringConfiguration": {
            "logUri": "s3://${aws_s3_bucket.data.id}/logs/"
          }
        }
      }'
    
    Cost: ~$0.05/vCPU-hour (pay only when running)
    
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  EOT
  description = "EMR Serverless usage example"
}
