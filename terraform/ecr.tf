# ECR Repository unique pour tout le projet PatternAlarm
resource "aws_ecr_repository" "main" {
  name                 = var.project_name
  image_tag_mutability = "MUTABLE"
  
  image_scanning_configuration {
    scan_on_push = false  # Dev mode - économie
  }
  
  tags = {
    Name = "${var.project_name}-repo"
  }
}

# Lifecycle policy - garder uniquement 10 dernières images
# (Plus élevé car on a 3 services dans le même repo)
resource "aws_ecr_lifecycle_policy" "main" {
  repository = aws_ecr_repository.main.name
  
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images total"
      selection = {
        tagStatus     = "any"
        countType     = "imageCountMoreThan"
        countNumber   = 10
      }
      action = {
        type = "expire"
      }
    }]
  })
}

# Output
output "ecr_repository_url" {
  value       = aws_ecr_repository.main.repository_url
  description = "ECR repository URL for PatternAlarm (all services use this repo with different tags)"
}
