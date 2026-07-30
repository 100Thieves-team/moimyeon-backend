resource "random_password" "db" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_db_subnet_group" "core" {
  name       = "${local.name}-core-db"
  subnet_ids = aws_subnet.private_db[*].id

  tags = merge(local.tags, {
    Name = "${local.name}-core-db-subnet-group"
  })
}

resource "aws_db_instance" "core" {
  identifier = "${local.name}-rds-mysql"

  engine         = "mysql"
  engine_version = var.mysql_engine_version
  instance_class = var.rds_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result
  port     = 3306

  db_subnet_group_name   = aws_db_subnet_group.core.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period   = var.db_backup_retention_period
  copy_tags_to_snapshot     = true
  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = var.db_skip_final_snapshot
  final_snapshot_identifier = var.db_skip_final_snapshot ? null : "${local.name}-rds-mysql-final"

  apply_immediately = var.environment == "dev"

  tags = merge(local.tags, {
    Name = "${local.name}-rds-mysql"
  })
}

resource "aws_ssm_parameter" "db_password" {
  name        = "/${var.project}/${var.environment}/core-api/DB_PASSWORD"
  description = "RDS password for ${local.name} core-api"
  type        = "SecureString"
  value       = random_password.db.result

  tags = local.tags
}
