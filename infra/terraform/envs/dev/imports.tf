# ---------------------------------------------------------------------------
# Phase 1 "absorb" — adopt the existing hand-built dev resources into state.
# Only the stable shared layer that maps cleanly is imported here; the ECS
# layer, the new ip target group, ECS IAM roles, SSM secrets, and the bastion
# instance are intentionally left as create (they don't exist yet / can't be
# imported due to a type change). Remove this file after the first apply that
# reconciles the import (blocks are idempotent, so keeping it is also fine).
#
# IDs captured from account 781897847312 on 2026-07-30.
# ---------------------------------------------------------------------------

# --- VPC / gateways --------------------------------------------------------
import {
  to = module.dev.aws_vpc.this
  id = "vpc-04bd6efce581313b0"
}

import {
  to = module.dev.aws_internet_gateway.this
  id = "igw-0b41b636855053976"
}

import {
  to = module.dev.aws_eip.nat[0]
  id = "eipalloc-01e77149faa45ff14"
}

import {
  to = module.dev.aws_nat_gateway.this[0]
  id = "nat-0c769c79718adadb6"
}

# --- Subnets (AZ order: 0=ap-northeast-2a, 1=ap-northeast-2c) ---------------
import {
  to = module.dev.aws_subnet.public[0]
  id = "subnet-0b2384c8074c3d0ce"
}

import {
  to = module.dev.aws_subnet.public[1]
  id = "subnet-069c5a81c2c24f817"
}

import {
  to = module.dev.aws_subnet.private_app[0]
  id = "subnet-0c5f3638261e90fb7"
}

import {
  to = module.dev.aws_subnet.private_app[1]
  id = "subnet-0d3cc87bed29187bc"
}

import {
  to = module.dev.aws_subnet.private_db[0]
  id = "subnet-0589a3384014a5dcf"
}

import {
  to = module.dev.aws_subnet.private_db[1]
  id = "subnet-0995d096200f157cf"
}

# --- Route tables + associations -------------------------------------------
import {
  to = module.dev.aws_route_table.public
  id = "rtb-05451afc1ad263976"
}

import {
  to = module.dev.aws_route_table.private_app
  id = "rtb-0291d5ceebb8e9b59"
}

import {
  to = module.dev.aws_route_table.private_db
  id = "rtb-09c609219a0a38a89"
}

import {
  to = module.dev.aws_route_table_association.public[0]
  id = "subnet-0b2384c8074c3d0ce/rtb-05451afc1ad263976"
}

import {
  to = module.dev.aws_route_table_association.public[1]
  id = "subnet-069c5a81c2c24f817/rtb-05451afc1ad263976"
}

import {
  to = module.dev.aws_route_table_association.private_app[0]
  id = "subnet-0c5f3638261e90fb7/rtb-0291d5ceebb8e9b59"
}

import {
  to = module.dev.aws_route_table_association.private_app[1]
  id = "subnet-0d3cc87bed29187bc/rtb-0291d5ceebb8e9b59"
}

import {
  to = module.dev.aws_route_table_association.private_db[0]
  id = "subnet-0589a3384014a5dcf/rtb-09c609219a0a38a89"
}

import {
  to = module.dev.aws_route_table_association.private_db[1]
  id = "subnet-0995d096200f157cf/rtb-09c609219a0a38a89"
}

# --- ALB + shared security groups ------------------------------------------
import {
  to = module.dev.aws_lb.app
  id = "arn:aws:elasticloadbalancing:ap-northeast-2:781897847312:loadbalancer/app/moimyeon-dev-alb/ea3a04a7ce0b19df"
}

import {
  to = module.dev.aws_security_group.alb
  id = "sg-05c9c150b7814d74f"
}

import {
  to = module.dev.aws_security_group.rds
  id = "sg-0db3fb521a96b85b8"
}

# --- RDS -------------------------------------------------------------------
import {
  to = module.dev.aws_db_subnet_group.core
  id = "moimyeon-dev-rds-subnet-group"
}

import {
  to = module.dev.aws_db_instance.core
  id = "moimyeon-dev-rds-mysql"
}

# --- ECR (existing shared repo) --------------------------------------------
import {
  to = module.dev.aws_ecr_repository.app
  id = "moimyeon/backend"
}

# --- S3 uploads bucket + configs (MOI-361) ---------------------------------
import {
  to = module.dev.aws_s3_bucket.uploads
  id = "moimyeon-dev-uploads"
}

import {
  to = module.dev.aws_s3_bucket_public_access_block.uploads
  id = "moimyeon-dev-uploads"
}

import {
  to = module.dev.aws_s3_bucket_server_side_encryption_configuration.uploads
  id = "moimyeon-dev-uploads"
}

import {
  to = module.dev.aws_s3_bucket_cors_configuration.uploads
  id = "moimyeon-dev-uploads"
}

import {
  to = module.dev.aws_s3_bucket_lifecycle_configuration.uploads
  id = "moimyeon-dev-uploads"
}

import {
  to = module.dev.aws_s3_bucket_policy.uploads
  id = "moimyeon-dev-uploads"
}

# --- S3 Gateway VPC endpoint (MOI-361) -------------------------------------
import {
  to = module.dev.aws_vpc_endpoint.s3[0]
  id = "vpce-02c192c984d8874df"
}
