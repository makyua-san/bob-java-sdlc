variable "project_name" { type = string }
variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "alb_security_group_id" { type = string }
variable "task_definition_arn" { type = string }
variable "target_group_arn" { type = string }
variable "desired_count" { type = number default = 2 }
variable "min_capacity" { type = number default = 2 }
variable "max_capacity" { type = number default = 4 }
