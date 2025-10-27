resource "aws_lambda_function" "event_generator" {
  # Utilise placeholder pour création initiale (CodeBuild déploiera le vrai code)
  filename         = "lambda-placeholder.zip"
  source_code_hash = filebase64sha256("lambda-placeholder.zip")
  
  function_name = "${var.project_name}-event-generator"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "lambda_handler.lambda_handler"
  runtime       = "python3.11"
  timeout       = 300
  memory_size   = 512  # 512 MB suffit largement

  vpc_config {
    subnet_ids         = [aws_subnet.private.id]
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
      last_modified
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

# Output
output "lambda_function_name" {
  value       = aws_lambda_function.event_generator.function_name
  description = "Lambda event generator function name"
}

output "lambda_function_arn" {
  value       = aws_lambda_function.event_generator.arn
  description = "Lambda event generator function ARN"
}
