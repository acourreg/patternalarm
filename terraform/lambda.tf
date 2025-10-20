resource "aws_lambda_function" "event_generator" {
  filename         = "../services/event-simulator/lambda-deployment.zip"
  function_name    = "patternalarm-event-generator"
  role            = aws_iam_role.lambda_exec.arn
  handler         = "lambda_handler.lambda_handler"
  runtime         = "python3.11"
  timeout         = 300
  memory_size     = 1024

  vpc_config {
    subnet_ids         = [aws_subnet.private.id]
   security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      KAFKA_BOOTSTRAP_SERVERS = aws_msk_cluster.main.bootstrap_brokers
    }
  }

  source_code_hash = filebase64sha256("../services/event-simulator/lambda-deployment.zip")
}