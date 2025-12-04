# Application Load Balancer pour Dashboard
# AWS Shield Standard est activé automatiquement (gratuit)

# Security Group pour ALB
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-sg"
  description = "Security group for Application Load Balancer"
  vpc_id      = aws_vpc.main.id
  
  # HTTP depuis internet
  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  # Outbound vers ECS tasks
  egress {
    description     = "To ECS dashboard"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }
  
  tags = {
    Name = "${var.project_name}-alb-sg"
  }
}

# Ajouter ingress rule pour permettre ALB → Dashboard
resource "aws_security_group_rule" "ecs_from_alb" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = aws_security_group.ecs_tasks.id
  source_security_group_id = aws_security_group.alb.id
  description              = "Allow ALB to access Dashboard"
}

# Application Load Balancer
resource "aws_lb" "dashboard" {
  name               = "${var.project_name}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public.id, aws_subnet.public_b.id]
  
  enable_deletion_protection = false  # Dev mode
  
  # AWS Shield Standard est activé par défaut (gratuit)
  # Pas besoin de configuration supplémentaire
  
  tags = {
    Name = "${var.project_name}-alb"
  }
}

# Target Group pour Dashboard ECS
resource "aws_lb_target_group" "dashboard" {
  name        = "${var.project_name}-dashboard-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"  # Fargate nécessite target_type = "ip"
  
  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 5      # était 3
    timeout             = 30     # était 5
    interval            = 60     # était 30
    path                = "/health"
    protocol            = "HTTP"
    matcher             = "200"
  }
  
  deregistration_delay = 30  # Secondes avant de retirer une task
  
  tags = {
    Name = "${var.project_name}-dashboard-tg"
  }
}

# Listener HTTP
resource "aws_lb_listener" "dashboard" {
  load_balancer_arn = aws_lb.dashboard.arn
  port              = 80
  protocol          = "HTTP"
  
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.dashboard.arn
  }
  
  tags = {
    Name = "${var.project_name}-alb-listener"
  }
}

# Outputs
output "alb_dns_name" {
  value       = aws_lb.dashboard.dns_name
  description = "DNS name of the ALB - access dashboard at http://<dns-name>"
}

output "alb_arn" {
  value       = aws_lb.dashboard.arn
  description = "ARN of the Application Load Balancer"
}

output "dashboard_url" {
  value       = "http://${aws_lb.dashboard.dns_name}"
  description = "Full URL to access the dashboard"
}
