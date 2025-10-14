variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "aws_profile" {
  description = "AWS CLI profile"
  type        = string
  default     = "terraform"
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "patternalarm"
}

variable "vpc_cidr" {
  description = "VPC CIDR"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zone" {
  description = "AZ for resources"
  type        = string
  default     = "us-east-1a"
}

variable "db_password" {
  description = "RDS master password"
  type        = string
  sensitive   = true
}

variable "availability_zones" {
  description = "List of AZs for MSK"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}