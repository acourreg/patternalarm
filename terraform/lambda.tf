# Lambda Event Generator
# Uses IAM role from iam.tf, CodeBuild deploys real code

# Génère un zip stub automatiquement
data "archive_file" "lambda_stub" {
  type        = "zip"
  output_path = "${path.module}/lambda-stub.zip"
  
  source {
    content  = "def handler(event, context): return {'statusCode': 200, 'body': 'Placeholder - deploy via CodeBuild'}"
    filename = "lambda_handler.py"
  }
}

# Lambda Function
resource "aws_lambda_function" "event_generator" {
  function_name = "${var.project_name}-event-generator"
  role          = aws_iam_role.lambda_exec.arn  # Uses existing role from iam.tf
  handler       = "lambda_handler.handler"  # Stub handler (sera écrasé par CodeBuild)
  runtime       = "python3.11"
  timeout       = 300
  memory_size   = 512
  
  # Stub zip (sera écrasé par CodeBuild)
  filename         = data.archive_file.lambda_stub.output_path
  source_code_hash = data.archive_file.lambda_stub.output_base64sha256

  vpc_config {
    subnet_ids = [
      aws_subnet.private.id,
      aws_subnet.private_b.id,
      aws_subnet.private_c.id
    ]
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      KAFKA_BOOTSTRAP_SERVERS = aws_msk_cluster.main.bootstrap_brokers
    }
  }
  
  # Ignore les changements de code (géré par CodeBuild)
  lifecycle {
    ignore_changes = [
      filename,
      source_code_hash,
      handler
    ]
  }
  
  tags = {
    Name = "${var.project_name}-event-generator"
  }
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "lambda_generator" {
  name              = "/aws/lambda/${var.project_name}-event-generator"
  retention_in_days = 7
  
  tags = {
    Name = "${var.project_name}-lambda-logs"
  }
}

# Outputs
output "lambda_function_name" {
  value       = aws_lambda_function.event_generator.function_name
  description = "Lambda event generator function name"
}

output "lambda_function_arn" {
  value       = aws_lambda_function.event_generator.arn
  description = "Lambda event generator function ARN"
}
