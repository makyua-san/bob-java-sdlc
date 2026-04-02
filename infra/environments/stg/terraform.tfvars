project_name = "purchase-request"
environment  = "stg"
region       = "ap-northeast-1"

vpc_cidr                 = "10.2.0.0/16"
public_subnet_cidrs      = ["10.2.1.0/24", "10.2.2.0/24"]
private_app_subnet_cidrs = ["10.2.10.0/24", "10.2.11.0/24"]
private_db_subnet_cidrs  = ["10.2.20.0/24", "10.2.21.0/24"]
availability_zones       = ["ap-northeast-1a", "ap-northeast-1c"]
