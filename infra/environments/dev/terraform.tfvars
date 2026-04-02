project_name = "purchase-request"
environment  = "dev"
region       = "ap-northeast-1"

vpc_cidr                 = "10.1.0.0/16"
public_subnet_cidrs      = ["10.1.1.0/24"]
private_app_subnet_cidrs = ["10.1.10.0/24"]
private_db_subnet_cidrs  = ["10.1.20.0/24"]
availability_zones       = ["ap-northeast-1a"]
