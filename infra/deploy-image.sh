#!/usr/bin/env bash
set -euo pipefail

# ══════════════════════════════════════════════════════════════════
# Learning Journey Builder — Build & Deploy Docker Image
#
# Usage:
#   ./infra/deploy-image.sh test          # Deploy to TEST
#   ./infra/deploy-image.sh prod          # Deploy to PROD
#   ./infra/deploy-image.sh test v1.2.3   # Deploy with specific tag
# ══════════════════════════════════════════════════════════════════

ENV="${1:-}"
TAG="${2:-latest}"

if [[ -z "$ENV" ]] || [[ "$ENV" != "test" && "$ENV" != "prod" ]]; then
  echo "Usage: $0 <test|prod> [tag]"
  echo ""
  echo "Examples:"
  echo "  $0 test           # Build and deploy to TEST"
  echo "  $0 prod           # Build and deploy to PROD"
  echo "  $0 test v1.2.3    # Deploy with tag v1.2.3"
  exit 1
fi

# ── Configuration ──
AWS_REGION="eu-west-1"
AWS_ACCOUNT="643502197318"
ECR_REPO="journeys-builder"
ECR_URI="${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}"
ECS_CLUSTER_NAME="XXXXXXXX"  # Same as in deploy-ecs.sh — fill in

if [[ "$ENV" == "test" ]]; then
  SERVICE_NAME="journeys-test"
  TASK_FAMILY="journeys-test-task"
else
  SERVICE_NAME="journeys-prod"
  TASK_FAMILY="journeys-prod-task"
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "═══════════════════════════════════════════════════════════"
echo "  Deploying to: $ENV"
echo "  Image tag:    $TAG"
echo "  ECR:          $ECR_URI"
echo "═══════════════════════════════════════════════════════════"

# ── Safety check for prod ──
if [[ "$ENV" == "prod" ]]; then
  echo ""
  echo "  WARNING: You are deploying to PRODUCTION!"
  read -p "  Type 'yes' to confirm: " CONFIRM
  if [[ "$CONFIRM" != "yes" ]]; then
    echo "  Aborted."
    exit 1
  fi
fi

# ── Step 1: Build frontend ──
echo ""
echo "── Step 1: Building frontend ──"
cd "$PROJECT_ROOT/frontend"
npm ci --silent
npm run build
echo "  Frontend built"

# ── Step 2: Copy frontend to backend webapp ──
echo ""
echo "── Step 2: Copying frontend to backend/src/main/webapp ──"
rm -rf "$PROJECT_ROOT/backend/src/main/webapp/assets"
cp -r "$PROJECT_ROOT/frontend/dist/assets" "$PROJECT_ROOT/backend/src/main/webapp/assets"
cp "$PROJECT_ROOT/frontend/dist/index.html" "$PROJECT_ROOT/backend/src/main/webapp/index.html"
echo "  Frontend assets copied"

# ── Step 3: ECR login ──
echo ""
echo "── Step 3: Authenticating with ECR ──"
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin "$AWS_ACCOUNT.dkr.ecr.${AWS_REGION}.amazonaws.com"
echo "  ECR authenticated"

# ── Step 4: Build Docker image ──
echo ""
echo "── Step 4: Building Docker image ──"
cd "$PROJECT_ROOT/backend"

GIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
BUILD_TIME=$(date -u +%Y%m%d-%H%M%S)

docker build \
  --platform linux/amd64 \
  --label "git.sha=$GIT_SHA" \
  --label "build.time=$BUILD_TIME" \
  --label "deploy.env=$ENV" \
  -t "$ECR_REPO:$TAG" \
  -t "$ECR_REPO:$GIT_SHA" \
  -t "$ECR_URI:$TAG" \
  -t "$ECR_URI:$GIT_SHA" \
  .

echo "  Docker image built: $ECR_REPO:$TAG ($GIT_SHA)"

# ── Step 5: Push to ECR ──
echo ""
echo "── Step 5: Pushing to ECR ──"
docker push "$ECR_URI:$TAG"
docker push "$ECR_URI:$GIT_SHA"
echo "  Pushed: $ECR_URI:$TAG"
echo "  Pushed: $ECR_URI:$GIT_SHA"

# ── Step 6: Update ECS service (force new deployment) ──
echo ""
echo "── Step 6: Updating ECS service ──"
aws ecs update-service \
  --cluster "$ECS_CLUSTER_NAME" \
  --service "$SERVICE_NAME" \
  --force-new-deployment \
  --region "$AWS_REGION" \
  --query 'service.{status:status,taskDef:taskDefinition}' \
  --output table

echo "  ECS service update triggered"

# ── Step 7: Wait for deployment to stabilize ──
echo ""
echo "── Step 7: Waiting for deployment to stabilize ──"
echo "  (this may take 2-5 minutes...)"

aws ecs wait services-stable \
  --cluster "$ECS_CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION"

echo "  Deployment stabilized!"

# ── Step 8: Verify ──
echo ""
echo "── Step 8: Verifying deployment ──"

if [[ "$ENV" == "test" ]]; then
  DOMAIN="journeys-test.mentes.me"
else
  DOMAIN="journeys.mentes.me"
fi

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "https://${DOMAIN}/api/health" 2>/dev/null || echo "000")

if [[ "$HTTP_STATUS" == "200" ]]; then
  echo "  Health check: OK (200)"
else
  echo "  Health check: FAILED (HTTP $HTTP_STATUS)"
  echo ""
  echo "  Check logs:"
  echo "    aws logs tail /ecs/journeys-${ENV} --region eu-west-1 --since 5m"
  echo ""
  echo "  Rollback:"
  echo "    aws ecs list-task-definitions --family-prefix $TASK_FAMILY --sort DESC --region eu-west-1 --query 'taskDefinitionArns[:3]'"
  echo "    aws ecs update-service --cluster $ECS_CLUSTER_NAME --service $SERVICE_NAME --task-definition $TASK_FAMILY:PREVIOUS_REVISION --region eu-west-1"
  exit 1
fi

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  Deployment complete!"
echo "  Environment: $ENV"
echo "  URL:         https://$DOMAIN"
echo "  Image:       $ECR_URI:$TAG ($GIT_SHA)"
echo "═══════════════════════════════════════════════════════════"
