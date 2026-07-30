# ---------------------------------------------------------------------------
# DB access bastion (optional). Private, SSM-managed, no SSH key / no public IP.
# Developers open an SSM port-forward through it to reach the private RDS.
# Mirrors the existing moimyeon-dev-role-ssm-db-access host.
# ---------------------------------------------------------------------------

# private mgmt subnet — offset 30 => 10.20.30.0/24 (single AZ)
resource "aws_subnet" "private_mgmt" {
  count = var.enable_db_bastion ? 1 : 0

  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, 30)
  availability_zone       = local.azs[0]
  map_public_ip_on_launch = false

  tags = merge(local.tags, {
    Name = "${local.name}-private-mgmt-1"
    Tier = "mgmt"
  })
}

# Reuse the private-app route table (NAT egress) so the bastion reaches the
# SSM endpoints; it has no default inbound route from the internet.
resource "aws_route_table_association" "private_mgmt" {
  count = var.enable_db_bastion ? 1 : 0

  subnet_id      = aws_subnet.private_mgmt[0].id
  route_table_id = aws_route_table.private_app.id
}

resource "aws_security_group" "db_bastion" {
  count = var.enable_db_bastion ? 1 : 0

  name        = "${local.name}-sg-db-access"
  description = "SSM DB access bastion (no inbound; SSM is outbound)"
  vpc_id      = aws_vpc.this.id

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.tags, {
    Name = "${local.name}-db-bastion-sg"
  })
}

resource "aws_iam_role" "db_bastion" {
  count = var.enable_db_bastion ? 1 : 0

  name               = "${local.name}-role-ssm-db-access"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "db_bastion_ssm" {
  count = var.enable_db_bastion ? 1 : 0

  role       = aws_iam_role.db_bastion[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "db_bastion" {
  count = var.enable_db_bastion ? 1 : 0

  name = "${local.name}-role-ssm-db-access"
  role = aws_iam_role.db_bastion[0].name

  tags = local.tags
}

resource "aws_instance" "db_bastion" {
  count = var.enable_db_bastion ? 1 : 0

  ami                    = data.aws_ssm_parameter.ecs_optimized_ami.value
  instance_type          = var.bastion_instance_type
  subnet_id              = aws_subnet.private_mgmt[0].id
  vpc_security_group_ids = [aws_security_group.db_bastion[0].id]
  iam_instance_profile   = aws_iam_instance_profile.db_bastion[0].name

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    volume_size = 8
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name}-role-ssm-db-access"
  })
}
