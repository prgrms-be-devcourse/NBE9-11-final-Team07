output "ec2_public_ip" {
  description = "Elastic IP (EIP) attached to EC2 instance"
  value       = aws_eip.main.public_ip
}

output "ec2_public_dns" {
  description = "EC2 instance public DNS"
  value       = aws_instance.main.public_dns
}

output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = aws_db_instance.main.endpoint
}

output "s3_bucket_name" {
  description = "S3 bucket name for popup images"
  value       = aws_s3_bucket.main.bucket
}

output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}
