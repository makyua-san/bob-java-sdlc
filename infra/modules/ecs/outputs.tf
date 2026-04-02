output "cluster_name" { value = aws_ecs_cluster.main.name }
output "service_name" { value = aws_ecs_service.main.name }
output "security_group_id" { value = aws_security_group.ecs.id }
