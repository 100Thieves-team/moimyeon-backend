resource "aws_lb" "app" {
  name               = "${local.name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection = var.environment == "live"

  tags = merge(local.tags, {
    Name = "${local.name}-alb"
  })
}

resource "aws_lb_target_group" "app" {
  name        = local.tg_name
  port        = var.container_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.this.id

  deregistration_delay = 30

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = var.health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = merge(local.tags, {
    Name = local.tg_name
  })
}

resource "aws_lb_listener" "http" {
  count = var.manage_alb_listeners ? 1 : 0

  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = local.https_enabled ? [] : [1]

    content {
      type             = "forward"
      target_group_arn = aws_lb_target_group.app.arn
    }
  }

  dynamic "default_action" {
    for_each = local.https_enabled ? [1] : []

    content {
      type = "redirect"

      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }

  tags = local.tags
}

data "aws_route53_zone" "selected" {
  count = local.route53_zone_lookup_needed ? 1 : 0

  name         = var.route53_zone_name
  private_zone = false
}

locals {
  selected_route53_zone_id = local.route53_zone_id_provided ? var.route53_zone_id : (
    local.route53_zone_lookup_needed ? data.aws_route53_zone.selected[0].zone_id : null
  )
}

resource "aws_acm_certificate" "app" {
  count = local.certificate_enabled ? 1 : 0

  domain_name       = var.app_domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = local.tags
}

resource "aws_route53_record" "app_certificate_validation" {
  for_each = local.route53_dns_enabled ? {
    for dvo in aws_acm_certificate.app[0].domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  } : {}

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = local.selected_route53_zone_id
}

resource "aws_acm_certificate_validation" "app" {
  count = local.https_enabled ? 1 : 0

  certificate_arn         = aws_acm_certificate.app[0].arn
  validation_record_fqdns = local.route53_dns_enabled ? [for record in aws_route53_record.app_certificate_validation : record.fqdn] : null
}

resource "aws_lb_listener" "https" {
  count = var.manage_alb_listeners && local.https_enabled ? 1 : 0

  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.app[0].certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }

  tags = local.tags
}

# Provisional listener so the ECS service can attach the otherwise-unrouted TG
# before the real listener cutover. Not reachable externally (ALB SG allows only
# 80/443); it exists solely to satisfy ECS's "TG must have a load balancer" rule
# and to let the ALB run TG health checks against the new tasks.
resource "aws_lb_listener" "ecs_provisional" {
  count = var.provisional_ecs_listener_port != null ? 1 : 0

  load_balancer_arn = aws_lb.app.arn
  port              = var.provisional_ecs_listener_port
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }

  tags = local.tags
}

resource "aws_route53_record" "app" {
  count = local.route53_dns_enabled ? 1 : 0

  zone_id = local.selected_route53_zone_id
  name    = var.app_domain_name
  type    = "A"

  alias {
    name                   = aws_lb.app.dns_name
    zone_id                = aws_lb.app.zone_id
    evaluate_target_health = true
  }
}
