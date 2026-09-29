output "organization_id" {
  value = aws_organizations_organization.personal.id
}

output "workload_ou_ids" {
  value = { for name, ou in aws_organizations_organizational_unit.workloads : name => ou.id }
}

output "guardrails_attached" {
  value = var.attach_guardrails
}
