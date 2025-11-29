# ============================================================================
# S3 BUCKET FOR EMR SPARK JOBS
# ============================================================================

resource "aws_s3_bucket" "data" {
  bucket = "${var.project_name}-data-${var.aws_region}"

  tags = {
    Name = "${var.project_name}-data"
  }
}

resource "aws_s3_bucket_versioning" "data" {
  bucket = aws_s3_bucket.data.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "data" {
  bucket = aws_s3_bucket.data.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Block public access
resource "aws_s3_bucket_public_access_block" "data" {
  bucket = aws_s3_bucket.data.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ============================================================================
# S3 FOLDER STRUCTURE (empty objects as placeholders)
# ============================================================================

resource "aws_s3_object" "folders" {
  for_each = toset([
    "spark-jobs/",
    "libs/",
    "data/raw/",
    "data/processed/",
    "data/features/",
    "models/",
    "logs/"
  ])

  bucket  = aws_s3_bucket.data.id
  key     = each.value
  content = ""
}

# ============================================================================
# OUTPUTS
# ============================================================================

output "s3_bucket_name" {
  value       = aws_s3_bucket.data.id
  description = "S3 bucket name for EMR data"
}

output "s3_bucket_arn" {
  value       = aws_s3_bucket.data.arn
  description = "S3 bucket ARN"
}

output "s3_paths" {
  value = {
    spark_jobs = "s3://${aws_s3_bucket.data.id}/spark-jobs/"
    libs       = "s3://${aws_s3_bucket.data.id}/libs/"
    raw_data   = "s3://${aws_s3_bucket.data.id}/data/raw/"
    processed  = "s3://${aws_s3_bucket.data.id}/data/processed/"
    features   = "s3://${aws_s3_bucket.data.id}/data/features/"
    models     = "s3://${aws_s3_bucket.data.id}/models/"
    logs       = "s3://${aws_s3_bucket.data.id}/logs/"
  }
  description = "S3 paths for different data types"
}
