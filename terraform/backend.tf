terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
  # TEMPORAIREMENT COMMENTÉ - Décommenter après premier apply
  # backend "s3" {
  #   bucket         = "patternalarm-terraform-state"
  #   key            = "dev/terraform.tfstate"
  #   region         = "us-east-1"
  #   profile        = "terraform"
  #   dynamodb_table = "patternalarm-terraform-locks"
  #   encrypt        = true
  # }
}