##############################################
# Common / Provider
##############################################

variable "aws_region" {
  description = "리소스를 생성할 AWS 리전"
  type        = string
  default     = "ap-northeast-2" # 서울
}

variable "project_name" {
  description = "리소스 네이밍에 사용할 prefix (예: 7sungsa-vpc, 7sungsa-ec2 ...)"
  type        = string
  default     = "team07"
}

variable "common_tags" {
  description = "모든 리소스에 공통으로 부여할 태그"
  type        = map(string)
  default = {
    Project = "popspot"
    Team    = "devcos-team07"
    Manager = "infra"
  }
}

##############################################
# Network (VPC / Subnet / AZ)
##############################################

variable "vpc_cidr" {
  description = "VPC CIDR 블록"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "사용할 가용영역 목록 (RDS 서브넷 그룹 제약: 최소 2개, 서로 다른 AZ)"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  description = "퍼블릭 서브넷 CIDR 목록 (availability_zones와 같은 순서/개수)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

##############################################
# EC2
##############################################

variable "ec2_instance_type" {
  description = "EC2 인스턴스 타입 (블루/그린 동시 실행 고려, t3.medium / 4GB)"
  type        = string
  default     = "t3.medium"
}

variable "key_pair_name" {
  description = "콘솔에서 미리 생성한 키페어 이름 (Private Key는 Terraform/state에 남기지 않음)"
  type        = string
  default     = "7sungsa-key"
}

variable "ssh_allowed_cidr" {
  description = "SSH(22) 접근 허용 CIDR. 현재는 전체 허용(0.0.0.0/0), 필요 시점에 좁힐 수 있음"
  type        = string
  default     = "0.0.0.0/0"
}

##############################################
# RDS
##############################################

variable "rds_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t3.micro"
}

variable "rds_engine_version" {
  description = "MySQL 엔진 버전"
  type        = string
  default     = "8.0"
}

variable "rds_allocated_storage" {
  description = "RDS 스토리지 크기 (GB)"
  type        = number
  default     = 20
}

variable "db_port" {
  description = "MySQL 포트"
  type        = number
  default     = 3306
}

variable "db_name" {
  description = "데이터베이스 이름"
  type        = string
  default     = "popspot_db"
}

variable "db_username" {
  description = "DB 마스터 유저명 (영문 시작, 16자 이하 - RDS 제약 충족)"
  type        = string
  default     = "popspot_7sungsa"
}

variable "db_password" {
  description = "DB 마스터 비밀번호 (8~41자, / \" @ 제외 - RDS 제약). 기본값 없음, terraform.tfvars에서만 주입"
  type        = string
  sensitive   = true
}

##############################################
# S3
##############################################

variable "s3_bucket_name" {
  description = "굿즈/팝업 이미지 저장용 S3 버킷 이름 (전역 유니크 필요, Private 버킷 + Pre-signed URL)"
  type        = string
  default     = "7sungsa-bucket-popup-images"
}
