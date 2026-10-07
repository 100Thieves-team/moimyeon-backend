locals {
  github_oidc_url = "https://token.actions.githubusercontent.com"
  route53_enabled = var.route53_zone_name != null && var.route53_zone_name != "" && var.create_hosted_zone

  tags = merge(
    {
      Project     = var.project
      Environment = "shared"
      ManagedBy   = "terraform"
    },
    var.tags,
  )
}

# The account may already have a GitHub OIDC provider (781897847312 does — used by
# plady-agent-platform / plady). Create only when create_oidc_provider = true;
# otherwise look up the existing one so downstream envs get a valid ARN either way.
resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_oidc_provider ? 1 : 0

  url             = local.github_oidc_url
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]

  tags = local.tags
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_oidc_provider ? 0 : 1

  url = local.github_oidc_url
}

locals {
  github_oidc_provider_arn = var.create_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

resource "aws_route53_zone" "this" {
  count = local.route53_enabled && var.create_hosted_zone ? 1 : 0

  name = var.route53_zone_name

  tags = local.tags
}

locals {
  route53_zone_id      = local.route53_enabled ? aws_route53_zone.this[0].zone_id : null
  route53_name_servers = local.route53_enabled ? aws_route53_zone.this[0].name_servers : []
}

resource "aws_route53domains_domain" "this" {
  count = var.register_domain ? 1 : 0

  domain_name       = var.route53_zone_name
  auto_renew        = var.domain_auto_renew
  duration_in_years = var.domain_duration_in_years

  admin_privacy      = var.domain_privacy_protection
  billing_privacy    = var.domain_privacy_protection
  registrant_privacy = var.domain_privacy_protection
  tech_privacy       = var.domain_privacy_protection

  dynamic "name_server" {
    for_each = local.route53_name_servers

    content {
      name = name_server.value
    }
  }

  admin_contact {
    address_line_1    = try(var.domain_contact.address_line_1, null)
    address_line_2    = try(var.domain_contact.address_line_2, null)
    city              = try(var.domain_contact.city, null)
    contact_type      = try(var.domain_contact.contact_type, "PERSON")
    country_code      = try(var.domain_contact.country_code, null)
    email             = try(var.domain_contact.email, null)
    fax               = try(var.domain_contact.fax, null)
    first_name        = try(var.domain_contact.first_name, null)
    last_name         = try(var.domain_contact.last_name, null)
    organization_name = try(var.domain_contact.organization_name, null)
    phone_number      = try(var.domain_contact.phone_number, null)
    state             = try(var.domain_contact.state, null)
    zip_code          = try(var.domain_contact.zip_code, null)
  }

  billing_contact {
    address_line_1    = try(var.domain_contact.address_line_1, null)
    address_line_2    = try(var.domain_contact.address_line_2, null)
    city              = try(var.domain_contact.city, null)
    contact_type      = try(var.domain_contact.contact_type, "PERSON")
    country_code      = try(var.domain_contact.country_code, null)
    email             = try(var.domain_contact.email, null)
    fax               = try(var.domain_contact.fax, null)
    first_name        = try(var.domain_contact.first_name, null)
    last_name         = try(var.domain_contact.last_name, null)
    organization_name = try(var.domain_contact.organization_name, null)
    phone_number      = try(var.domain_contact.phone_number, null)
    state             = try(var.domain_contact.state, null)
    zip_code          = try(var.domain_contact.zip_code, null)
  }

  registrant_contact {
    address_line_1    = try(var.domain_contact.address_line_1, null)
    address_line_2    = try(var.domain_contact.address_line_2, null)
    city              = try(var.domain_contact.city, null)
    contact_type      = try(var.domain_contact.contact_type, "PERSON")
    country_code      = try(var.domain_contact.country_code, null)
    email             = try(var.domain_contact.email, null)
    fax               = try(var.domain_contact.fax, null)
    first_name        = try(var.domain_contact.first_name, null)
    last_name         = try(var.domain_contact.last_name, null)
    organization_name = try(var.domain_contact.organization_name, null)
    phone_number      = try(var.domain_contact.phone_number, null)
    state             = try(var.domain_contact.state, null)
    zip_code          = try(var.domain_contact.zip_code, null)
  }

  tech_contact {
    address_line_1    = try(var.domain_contact.address_line_1, null)
    address_line_2    = try(var.domain_contact.address_line_2, null)
    city              = try(var.domain_contact.city, null)
    contact_type      = try(var.domain_contact.contact_type, "PERSON")
    country_code      = try(var.domain_contact.country_code, null)
    email             = try(var.domain_contact.email, null)
    fax               = try(var.domain_contact.fax, null)
    first_name        = try(var.domain_contact.first_name, null)
    last_name         = try(var.domain_contact.last_name, null)
    organization_name = try(var.domain_contact.organization_name, null)
    phone_number      = try(var.domain_contact.phone_number, null)
    state             = try(var.domain_contact.state, null)
    zip_code          = try(var.domain_contact.zip_code, null)
  }

  tags = local.tags
}
