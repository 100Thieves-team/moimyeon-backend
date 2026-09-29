resource "aws_organizations_organization" "personal" {
  feature_set                   = "ALL"
  enabled_policy_types          = var.enabled_policy_types
  aws_service_access_principals = var.aws_service_access_principals
}

resource "aws_organizations_organizational_unit" "workloads" {
  for_each  = var.ou_names
  name      = each.value
  parent_id = aws_organizations_organization.personal.roots[0].id
}

resource "aws_organizations_policy" "deny_iam_users" {
  name        = "deny-iam-users-and-access-keys"
  description = "Require federated or role-based access instead of IAM users and long-lived access keys."
  type        = "SERVICE_CONTROL_POLICY"
  content = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "DenyIamUsersAndAccessKeys"
      Effect   = "Deny"
      Action   = ["iam:CreateUser", "iam:CreateAccessKey", "iam:UpdateAccessKey", "iam:CreateLoginProfile", "iam:UpdateLoginProfile"]
      Resource = "*"
    }]
  })
}

resource "aws_organizations_policy" "deny_other_regions" {
  name        = "deny-unapproved-regions"
  description = "Deny regional API requests outside the approved Regions, with global service exemptions."
  type        = "SERVICE_CONTROL_POLICY"
  content = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "DenyUnapprovedRegions"
      Effect = "Deny"
      NotAction = [
        "account:*",
        "budgets:*",
        "cloudfront:*",
        "health:*",
        "iam:*",
        "networkmanager:*",
        "organizations:*",
        "pricing:*",
        "route53:*",
        "route53domains:*",
        "support:*",
      ]
      Resource = "*"
      Condition = {
        StringNotEquals = {
          "aws:RequestedRegion" = sort(tolist(var.allowed_regions))
        }
      }
    }]
  })
}

resource "aws_organizations_policy_attachment" "deny_iam_users" {
  for_each  = var.attach_guardrails ? var.ou_names : toset([])
  policy_id = aws_organizations_policy.deny_iam_users.id
  target_id = aws_organizations_organizational_unit.workloads[each.key].id
}

resource "aws_organizations_policy_attachment" "deny_other_regions" {
  for_each  = var.attach_guardrails ? var.ou_names : toset([])
  policy_id = aws_organizations_policy.deny_other_regions.id
  target_id = aws_organizations_organizational_unit.workloads[each.key].id
}
