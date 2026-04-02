output "alerts_topic_arn" { value = aws_sns_topic.alerts.arn }
output "critical_alerts_topic_arn" { value = aws_sns_topic.critical_alerts.arn }
