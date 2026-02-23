#!/usr/bin/env bash
set -euo pipefail

# ══════════════════════════════════════════════════════════════════
# Learning Journey Builder — ECS Fargate Infrastructure Setup
# Region: eu-west-1
# Account: 643502197318
# ══════════════════════════════════════════════════════════════════

# ── CONFIGURATION — FILL THESE IN ────────────────────────────────

AWS_REGION="eu-west-1"
AWS_ACCOUNT="643502197318"

# Existing resources (find via AWS Console or CLI)
VPC_ID="vpc-XXXXXXXX"                          # Your VPC
PRIVATE_SUBNET_1="subnet-XXXXXXXX"             # Private subnet AZ-a
PRIVATE_SUBNET_2="subnet-XXXXXXXX"             # Private subnet AZ-b
ALB_ARN="arn:aws:elasticloadbalancing:eu-west-1:643502197318:loadbalancer/app/XXXXXXXX"
ALB_HTTPS_LISTENER_ARN="arn:aws:elasticloadbalancing:eu-west-1:643502197318:listener/app/XXXXXXXX"
ALB_SECURITY_GROUP="sg-XXXXXXXX"               # ALB's security group
ECS_CLUSTER_NAME="XXXXXXXX"                    # Existing ECS cluster
HOSTED_ZONE_ID="ZXXXXXXXX"                     # Route53 hosted zone for mentes.me
ALB_HOSTED_ZONE_ID="Z32O12XQLNTSW2"           # ALB hosted zone (eu-west-1 default)
ALB_DNS_NAME="XXXXXXXX.eu-west-1.elb.amazonaws.com"

# ECR repository name
ECR_REPO="journeys-builder"

# RDS endpoint (same Metro DB)
METRO_DB_HOST="test-metro-db.cyi4arp1bouk.eu-west-1.rds.amazonaws.com"

echo "═══════════════════════════════════════════════════════════"
echo "  Learning Journey Builder — AWS Infrastructure Setup"
echo "═══════════════════════════════════════════════════════════"

# ═════════════════════════════════════════════════════════════════
# STEP 1: ECR Repository
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 1: Creating ECR repository ──"

aws ecr create-repository \
  --repository-name "$ECR_REPO" \
  --region "$AWS_REGION" \
  --image-scanning-configuration scanOnPush=true \
  --encryption-configuration encryptionType=AES256 \
  2>/dev/null || echo "  (repository already exists)"

ECR_URI="${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}"
echo "  ECR URI: $ECR_URI"

# ═════════════════════════════════════════════════════════════════
# STEP 2: CloudWatch Log Groups
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 2: Creating CloudWatch log groups ──"

aws logs create-log-group \
  --log-group-name "/ecs/journeys-test" \
  --region "$AWS_REGION" \
  2>/dev/null || echo "  /ecs/journeys-test already exists"

aws logs create-log-group \
  --log-group-name "/ecs/journeys-prod" \
  --region "$AWS_REGION" \
  2>/dev/null || echo "  /ecs/journeys-prod already exists"

# Set retention to 30 days
aws logs put-retention-policy --log-group-name "/ecs/journeys-test" --retention-in-days 30 --region "$AWS_REGION"
aws logs put-retention-policy --log-group-name "/ecs/journeys-prod" --retention-in-days 90 --region "$AWS_REGION"

echo "  Log groups created with retention policies"

# ═════════════════════════════════════════════════════════════════
# STEP 3: Security Group for ECS tasks
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 3: Creating ECS task security group ──"

ECS_SG=$(aws ec2 create-security-group \
  --group-name "sg-journeys-ecs-tasks" \
  --description "Security group for Learning Journey ECS tasks" \
  --vpc-id "$VPC_ID" \
  --region "$AWS_REGION" \
  --query 'GroupId' --output text \
  2>/dev/null || aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=sg-journeys-ecs-tasks" "Name=vpc-id,Values=$VPC_ID" \
    --query 'SecurityGroups[0].GroupId' --output text --region "$AWS_REGION")

# Allow inbound from ALB only on port 8080
aws ec2 authorize-security-group-ingress \
  --group-id "$ECS_SG" \
  --protocol tcp \
  --port 8080 \
  --source-group "$ALB_SECURITY_GROUP" \
  --region "$AWS_REGION" \
  2>/dev/null || echo "  (ingress rule already exists)"

echo "  ECS Security Group: $ECS_SG"

# ═════════════════════════════════════════════════════════════════
# STEP 4: IAM — Task Execution Role + Task Role
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 4: Creating IAM roles ──"

# Task Execution Role (ECS agent uses this to pull images, write logs)
cat > /tmp/ecs-trust-policy.json << 'TRUST'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "ecs-tasks.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}
TRUST

aws iam create-role \
  --role-name "journeys-ecs-execution-role" \
  --assume-role-policy-document file:///tmp/ecs-trust-policy.json \
  2>/dev/null || echo "  (execution role already exists)"

aws iam attach-role-policy \
  --role-name "journeys-ecs-execution-role" \
  --policy-arn "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy" \
  2>/dev/null || true

# SSM read permission (needed to inject secrets into container env vars)
cat > /tmp/ssm-read-policy.json << 'SSMPOLICY'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "ssm:GetParameters",
      "ssm:GetParameter"
    ],
    "Resource": "arn:aws:ssm:eu-west-1:643502197318:parameter/journeys/*"
  }]
}
SSMPOLICY

aws iam put-role-policy \
  --role-name "journeys-ecs-execution-role" \
  --policy-name "journeys-ssm-read" \
  --policy-document file:///tmp/ssm-read-policy.json

# Task Role (the application uses this for S3 access etc.)
aws iam create-role \
  --role-name "journeys-ecs-task-role" \
  --assume-role-policy-document file:///tmp/ecs-trust-policy.json \
  2>/dev/null || echo "  (task role already exists)"

# S3 access for the task
cat > /tmp/s3-policy.json << S3POLICY
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:ListBucket"],
    "Resource": [
      "arn:aws:s3:::metro-platform",
      "arn:aws:s3:::metro-platform/*"
    ]
  }]
}
S3POLICY

aws iam put-role-policy \
  --role-name "journeys-ecs-task-role" \
  --policy-name "journeys-s3-access" \
  --policy-document file:///tmp/s3-policy.json

echo "  IAM roles configured"

# ═════════════════════════════════════════════════════════════════
# STEP 5: ACM Certificates
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 5: Requesting ACM certificates ──"

# Request cert for both domains (single SAN cert)
CERT_ARN=$(aws acm request-certificate \
  --domain-name "journeys.mentes.me" \
  --subject-alternative-names "journeys-test.mentes.me" \
  --validation-method DNS \
  --region "$AWS_REGION" \
  --query 'CertificateArn' --output text)

echo "  Certificate ARN: $CERT_ARN"
echo ""
echo "  ⚠️  MANUAL STEP REQUIRED:"
echo "  1. Go to ACM console → find this certificate"
echo "  2. Click 'Create records in Route53' to validate"
echo "  3. Wait for status = ISSUED (usually 2-5 minutes)"
echo "  4. Then continue with Step 6"
echo ""
read -p "  Press Enter when certificate is ISSUED..."

# Add cert to ALB HTTPS listener
aws elbv2 add-listener-certificates \
  --listener-arn "$ALB_HTTPS_LISTENER_ARN" \
  --certificates CertificateArn="$CERT_ARN" \
  --region "$AWS_REGION"

echo "  Certificate attached to ALB listener"

# ═════════════════════════════════════════════════════════════════
# STEP 6: Target Groups
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 6: Creating target groups ──"

TG_TEST_ARN=$(aws elbv2 create-target-group \
  --name "tg-journeys-test" \
  --protocol HTTP \
  --port 8080 \
  --vpc-id "$VPC_ID" \
  --target-type ip \
  --health-check-path "/api/health" \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --matcher HttpCode=200 \
  --region "$AWS_REGION" \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

TG_PROD_ARN=$(aws elbv2 create-target-group \
  --name "tg-journeys-prod" \
  --protocol HTTP \
  --port 8080 \
  --vpc-id "$VPC_ID" \
  --target-type ip \
  --health-check-path "/api/health" \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --matcher HttpCode=200 \
  --region "$AWS_REGION" \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

echo "  Test TG: $TG_TEST_ARN"
echo "  Prod TG: $TG_PROD_ARN"

# ═════════════════════════════════════════════════════════════════
# STEP 7: ALB Listener Rules (host-based routing)
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 7: Creating ALB listener rules ──"

aws elbv2 create-rule \
  --listener-arn "$ALB_HTTPS_LISTENER_ARN" \
  --priority 10 \
  --conditions "Field=host-header,Values=journeys-test.mentes.me" \
  --actions "Type=forward,TargetGroupArn=$TG_TEST_ARN" \
  --region "$AWS_REGION"

aws elbv2 create-rule \
  --listener-arn "$ALB_HTTPS_LISTENER_ARN" \
  --priority 11 \
  --conditions "Field=host-header,Values=journeys.mentes.me" \
  --actions "Type=forward,TargetGroupArn=$TG_PROD_ARN" \
  --region "$AWS_REGION"

echo "  Listener rules created (priority 10=test, 11=prod)"

# ═════════════════════════════════════════════════════════════════
# STEP 8: Route53 DNS Records
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 8: Creating Route53 DNS records ──"

cat > /tmp/route53-records.json << DNS
{
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "journeys-test.mentes.me",
        "Type": "A",
        "AliasTarget": {
          "HostedZoneId": "${ALB_HOSTED_ZONE_ID}",
          "DNSName": "${ALB_DNS_NAME}",
          "EvaluateTargetHealth": true
        }
      }
    },
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "journeys.mentes.me",
        "Type": "A",
        "AliasTarget": {
          "HostedZoneId": "${ALB_HOSTED_ZONE_ID}",
          "DNSName": "${ALB_DNS_NAME}",
          "EvaluateTargetHealth": true
        }
      }
    }
  ]
}
DNS

aws route53 change-resource-record-sets \
  --hosted-zone-id "$HOSTED_ZONE_ID" \
  --change-batch file:///tmp/route53-records.json

echo "  DNS records created"

# ═════════════════════════════════════════════════════════════════
# STEP 9: ECS Task Definitions
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 9: Registering ECS task definitions ──"

EXEC_ROLE_ARN="arn:aws:iam::${AWS_ACCOUNT}:role/journeys-ecs-execution-role"
TASK_ROLE_ARN="arn:aws:iam::${AWS_ACCOUNT}:role/journeys-ecs-task-role"

# ── TEST task definition ──
cat > /tmp/task-def-test.json << TASKTEST
{
  "family": "journeys-test-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "${EXEC_ROLE_ARN}",
  "taskRoleArn": "${TASK_ROLE_ARN}",
  "containerDefinitions": [{
    "name": "journeys",
    "image": "${ECR_URI}:latest",
    "essential": true,
    "portMappings": [{
      "containerPort": 8080,
      "protocol": "tcp"
    }],
    "environment": [
      { "name": "ENV", "value": "TEST" },
      { "name": "BUILDER_AUTH_ENABLED", "value": "true" },
      { "name": "BUILDER_S3_ENABLED", "value": "true" },
      { "name": "BUILDER_S3_BUCKET", "value": "metro-platform" },
      { "name": "BUILDER_S3_REGION", "value": "eu-west-1" },
      { "name": "BUILDER_S3_PREFIX", "value": "test" },
      { "name": "BUILDER_METRO_PROD_ENABLED", "value": "false" }
    ],
    "secrets": [
      { "name": "BUILDER_METRO_URL", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/test/metro-url" },
      { "name": "BUILDER_METRO_USERNAME", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/test/metro-username" },
      { "name": "BUILDER_METRO_PASSWORD", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/test/metro-password" },
      { "name": "BUILDER_AUTH_USER", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/test/auth-user" },
      { "name": "BUILDER_AUTH_PASSWORD", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/test/auth-password" },
      { "name": "BUILDER_AUTH_TOKEN_SECRET", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/test/token-secret" }
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/journeys-test",
        "awslogs-region": "${AWS_REGION}",
        "awslogs-stream-prefix": "ecs"
      }
    },
    "healthCheck": {
      "command": ["CMD-SHELL", "curl -f http://localhost:8080/api/health || exit 1"],
      "interval": 30,
      "timeout": 5,
      "retries": 3,
      "startPeriod": 60
    }
  }]
}
TASKTEST

# ── PROD task definition ──
cat > /tmp/task-def-prod.json << TASKPROD
{
  "family": "journeys-prod-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "${EXEC_ROLE_ARN}",
  "taskRoleArn": "${TASK_ROLE_ARN}",
  "containerDefinitions": [{
    "name": "journeys",
    "image": "${ECR_URI}:latest",
    "essential": true,
    "portMappings": [{
      "containerPort": 8080,
      "protocol": "tcp"
    }],
    "environment": [
      { "name": "ENV", "value": "PROD" },
      { "name": "BUILDER_AUTH_ENABLED", "value": "true" },
      { "name": "BUILDER_S3_ENABLED", "value": "true" },
      { "name": "BUILDER_S3_BUCKET", "value": "metro-platform" },
      { "name": "BUILDER_S3_REGION", "value": "eu-west-1" },
      { "name": "BUILDER_S3_PREFIX", "value": "prod" },
      { "name": "BUILDER_METRO_PROD_ENABLED", "value": "true" }
    ],
    "secrets": [
      { "name": "BUILDER_METRO_URL", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/metro-url" },
      { "name": "BUILDER_METRO_USERNAME", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/metro-username" },
      { "name": "BUILDER_METRO_PASSWORD", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/metro-password" },
      { "name": "BUILDER_METRO_PROD_URL", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/metro-prod-url" },
      { "name": "BUILDER_METRO_PROD_USERNAME", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/metro-prod-username" },
      { "name": "BUILDER_METRO_PROD_PASSWORD", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/metro-prod-password" },
      { "name": "BUILDER_AUTH_USER", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/auth-user" },
      { "name": "BUILDER_AUTH_PASSWORD", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/auth-password" },
      { "name": "BUILDER_AUTH_TOKEN_SECRET", "valueFrom": "arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT}:parameter/journeys/prod/token-secret" }
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/journeys-prod",
        "awslogs-region": "${AWS_REGION}",
        "awslogs-stream-prefix": "ecs"
      }
    },
    "healthCheck": {
      "command": ["CMD-SHELL", "curl -f http://localhost:8080/api/health || exit 1"],
      "interval": 30,
      "timeout": 5,
      "retries": 3,
      "startPeriod": 60
    }
  }]
}
TASKPROD

aws ecs register-task-definition \
  --cli-input-json file:///tmp/task-def-test.json \
  --region "$AWS_REGION"

aws ecs register-task-definition \
  --cli-input-json file:///tmp/task-def-prod.json \
  --region "$AWS_REGION"

echo "  Task definitions registered"

# ═════════════════════════════════════════════════════════════════
# STEP 10: ECS Services
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 10: Creating ECS services ──"

# ── TEST service ──
aws ecs create-service \
  --cluster "$ECS_CLUSTER_NAME" \
  --service-name "journeys-test" \
  --task-definition "journeys-test-task" \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$PRIVATE_SUBNET_1,$PRIVATE_SUBNET_2],securityGroups=[$ECS_SG],assignPublicIp=DISABLED}" \
  --load-balancers "targetGroupArn=$TG_TEST_ARN,containerName=journeys,containerPort=8080" \
  --health-check-grace-period-seconds 60 \
  --deployment-configuration "minimumHealthyPercent=100,maximumPercent=200" \
  --region "$AWS_REGION"

# ── PROD service ──
aws ecs create-service \
  --cluster "$ECS_CLUSTER_NAME" \
  --service-name "journeys-prod" \
  --task-definition "journeys-prod-task" \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$PRIVATE_SUBNET_1,$PRIVATE_SUBNET_2],securityGroups=[$ECS_SG],assignPublicIp=DISABLED}" \
  --load-balancers "targetGroupArn=$TG_PROD_ARN,containerName=journeys,containerPort=8080" \
  --health-check-grace-period-seconds 60 \
  --deployment-configuration "minimumHealthyPercent=100,maximumPercent=200" \
  --region "$AWS_REGION"

echo "  ECS services created"

# ═════════════════════════════════════════════════════════════════
# STEP 11: Auto Scaling
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 11: Configuring auto scaling ──"

for SVC in journeys-test journeys-prod; do
  RESOURCE_ID="service/${ECS_CLUSTER_NAME}/${SVC}"

  aws application-autoscaling register-scalable-target \
    --service-namespace ecs \
    --resource-id "$RESOURCE_ID" \
    --scalable-dimension "ecs:service:DesiredCount" \
    --min-capacity 1 \
    --max-capacity 2 \
    --region "$AWS_REGION"

  aws application-autoscaling put-scaling-policy \
    --service-namespace ecs \
    --resource-id "$RESOURCE_ID" \
    --scalable-dimension "ecs:service:DesiredCount" \
    --policy-name "${SVC}-cpu-scaling" \
    --policy-type TargetTrackingScaling \
    --target-tracking-scaling-policy-configuration '{
      "TargetValue": 70.0,
      "PredefinedMetricSpecification": {
        "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
      },
      "ScaleInCooldown": 300,
      "ScaleOutCooldown": 60
    }' \
    --region "$AWS_REGION"
done

echo "  Auto scaling configured (min=1, max=2, target CPU=70%)"

# ═════════════════════════════════════════════════════════════════
# STEP 12: SSM Parameter Store (secrets)
# ═════════════════════════════════════════════════════════════════

echo ""
echo "── STEP 12: Creating SSM parameters for secrets ──"
echo ""
echo "  ⚠️  MANUAL STEP: Fill in the actual secret values below."
echo "  The commands below use placeholder values — replace before running."
echo ""

cat << 'SSMHELP'
  # ── TEST environment secrets ──
  aws ssm put-parameter --name "/journeys/test/metro-url" \
    --type SecureString --value "jdbc:mysql://test-metro-db.cyi4arp1bouk.eu-west-1.rds.amazonaws.com:3306/metro" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/test/metro-username" \
    --type SecureString --value "YOUR_METRO_TEST_USERNAME" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/test/metro-password" \
    --type SecureString --value "YOUR_METRO_TEST_PASSWORD" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/test/auth-user" \
    --type SecureString --value "YOUR_AUTH_USERNAME" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/test/auth-password" \
    --type SecureString --value "YOUR_AUTH_PASSWORD" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/test/token-secret" \
    --type SecureString --value "YOUR_JWT_SECRET_TEST" \
    --region eu-west-1 --overwrite

  # ── PROD environment secrets ──
  aws ssm put-parameter --name "/journeys/prod/metro-url" \
    --type SecureString --value "jdbc:mysql://test-metro-db.cyi4arp1bouk.eu-west-1.rds.amazonaws.com:3306/metro" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/prod/metro-username" \
    --type SecureString --value "YOUR_METRO_PROD_USERNAME" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/prod/metro-password" \
    --type SecureString --value "YOUR_METRO_PROD_PASSWORD" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/prod/metro-prod-url" \
    --type SecureString --value "jdbc:mysql://PROD_RDS_ENDPOINT:3306/metro" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/prod/metro-prod-username" \
    --type SecureString --value "YOUR_METRO_PROD_RW_USERNAME" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/prod/metro-prod-password" \
    --type SecureString --value "YOUR_METRO_PROD_RW_PASSWORD" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/prod/auth-user" \
    --type SecureString --value "YOUR_AUTH_USERNAME" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/prod/auth-password" \
    --type SecureString --value "YOUR_AUTH_PASSWORD" \
    --region eu-west-1 --overwrite

  aws ssm put-parameter --name "/journeys/prod/token-secret" \
    --type SecureString --value "YOUR_JWT_SECRET_PROD" \
    --region eu-west-1 --overwrite
SSMHELP

echo ""
echo "  Copy the commands above, fill in real values, and run them."
echo ""

# ═════════════════════════════════════════════════════════════════
# DONE
# ═════════════════════════════════════════════════════════════════

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  Infrastructure setup complete!"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "  Next steps:"
echo "  1. Fill in and run the SSM parameter commands above"
echo "  2. Build & push Docker image: ./infra/deploy-image.sh test"
echo "  3. Verify: see VERIFICATION CHECKLIST below"
echo ""

cat << 'VERIFY'
═══════════════════════════════════════════════════════════════
  VERIFICATION CHECKLIST
═══════════════════════════════════════════════════════════════

  1. DNS Resolution
     dig journeys-test.mentes.me
     dig journeys.mentes.me
     → Should resolve to ALB IP addresses

  2. ACM Certificate
     aws acm describe-certificate --certificate-arn $CERT_ARN --region eu-west-1 \
       --query 'Certificate.Status'
     → Should return "ISSUED"

  3. SSM Parameters exist
     aws ssm get-parameters-by-path --path "/journeys/test/" --region eu-west-1 \
       --query 'Parameters[].Name'
     aws ssm get-parameters-by-path --path "/journeys/prod/" --region eu-west-1 \
       --query 'Parameters[].Name'
     → Should list all parameter names

  4. ECS Service running
     aws ecs describe-services --cluster $ECS_CLUSTER_NAME \
       --services journeys-test --region eu-west-1 \
       --query 'services[0].{status:status,running:runningCount,desired:desiredCount}'
     → status=ACTIVE, running=desired=1

  5. Health check
     curl -s https://journeys-test.mentes.me/api/health
     → Should return 200 OK

  6. Target Group health
     aws elbv2 describe-target-health --target-group-arn $TG_TEST_ARN --region eu-west-1
     → State should be "healthy"

  7. CloudWatch Logs
     aws logs tail /ecs/journeys-test --region eu-west-1 --since 5m
     → Should show Spring Boot startup logs

═══════════════════════════════════════════════════════════════
  ROLLBACK STEPS
═══════════════════════════════════════════════════════════════

  Rollback a bad deployment (revert to previous task definition):
  ────────────────────────────────────────────────────────────
  # 1. Find previous working revision
  aws ecs list-task-definitions --family-prefix journeys-test-task \
    --sort DESC --region eu-west-1 --query 'taskDefinitionArns[:3]'

  # 2. Update service to previous revision
  aws ecs update-service --cluster $ECS_CLUSTER_NAME \
    --service journeys-test \
    --task-definition journeys-test-task:PREVIOUS_REVISION \
    --region eu-west-1

  # 3. Wait for rollback to stabilize
  aws ecs wait services-stable --cluster $ECS_CLUSTER_NAME \
    --services journeys-test --region eu-west-1

  Delete entire environment (nuclear option):
  ────────────────────────────────────────────────────────────
  # 1. Scale service to 0 first
  aws ecs update-service --cluster $ECS_CLUSTER_NAME \
    --service journeys-test --desired-count 0 --region eu-west-1

  # 2. Delete service
  aws ecs delete-service --cluster $ECS_CLUSTER_NAME \
    --service journeys-test --force --region eu-west-1

  # 3. Remove ALB listener rule (find rule ARN first)
  aws elbv2 describe-rules --listener-arn $ALB_HTTPS_LISTENER_ARN --region eu-west-1
  aws elbv2 delete-rule --rule-arn RULE_ARN --region eu-west-1

  # 4. Delete target group
  aws elbv2 delete-target-group --target-group-arn $TG_TEST_ARN --region eu-west-1

  # 5. Remove DNS record
  # (edit /tmp/route53-records.json: change "UPSERT" to "DELETE")
  aws route53 change-resource-record-sets --hosted-zone-id $HOSTED_ZONE_ID \
    --change-batch file:///tmp/route53-records.json

  # Repeat for prod if needed (replace journeys-test → journeys-prod)
VERIFY
