#!/usr/bin/env bash
set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════
# deploy-journeys.sh — Deploy Learning Journey Builder
# ═══════════════════════════════════════════════════════════════════════
#
# Usage: ./deploy-journeys.sh test
#        ./deploy-journeys.sh prod
#
# EB Application: journeys-builder
# Environments:   journeys-test, journeys-prod
# S3 prefix:      journeys/
# ═══════════════════════════════════════════════════════════════════════

readonly APP_NAME="journeys-builder"
readonly S3_BUCKET="elasticbeanstalk-eu-west-1-643502197318"
readonly S3_PREFIX="journeys"
readonly REGION="eu-west-1"
readonly SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly BACKEND_DIR="${SCRIPT_DIR}/backend"

# ── Argument validation ──────────────────────────────────────────────

if [[ $# -ne 1 ]]; then
    echo "ERROR: Exactly one argument required: test or prod"
    echo "Usage: $0 test|prod"
    exit 1
fi

ENV_ARG="$1"

case "${ENV_ARG}" in
    test)
        ENV_NAME="journeys-test"
        ;;
    prod)
        ENV_NAME="journeys-prod"
        ;;
    Metro-builder-env|metro-builder-prod|Metro-builder*)
        echo "ERROR: This script deploys to journeys-builder, NOT metro-builder."
        echo "       Use deploy-assessment.sh for metro-builder environments."
        exit 1
        ;;
    *)
        echo "ERROR: Invalid argument '${ENV_ARG}'. Must be 'test' or 'prod'."
        exit 1
        ;;
esac

# ── Prerequisite checks ─────────────────────────────────────────────

if ! command -v aws &>/dev/null; then
    echo "ERROR: aws CLI not found. Install it first."
    exit 1
fi

if ! command -v mvn &>/dev/null; then
    echo "ERROR: mvn not found. Install Maven first."
    exit 1
fi

if ! aws sts get-caller-identity &>/dev/null; then
    echo "ERROR: AWS credentials not configured. Run 'aws configure' or set AWS_PROFILE."
    exit 1
fi

# ── Git checks ─────────────────────────────────────────────────────

cd "${SCRIPT_DIR}"

# 1. Dirty check: refuse to deploy uncommitted changes
if [[ -n "$(git status --porcelain)" ]]; then
    echo "ERROR: Working directory has uncommitted changes."
    echo ""
    git status --short
    echo ""
    echo "Commit or stash your changes before deploying."
    exit 1
fi

# 2. Get git commit hash for version traceability
GIT_HASH="$(git rev-parse --short=7 HEAD)"
GIT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
echo "Git: ${GIT_BRANCH} @ ${GIT_HASH}"

# 3. Push check: warn if local is ahead of remote
LOCAL_HEAD="$(git rev-parse HEAD)"
REMOTE_HEAD="$(git rev-parse origin/${GIT_BRANCH} 2>/dev/null || echo 'unknown')"
if [[ "${REMOTE_HEAD}" == "unknown" ]]; then
    echo "WARNING: No remote tracking branch for '${GIT_BRANCH}'."
elif [[ "${LOCAL_HEAD}" != "${REMOTE_HEAD}" ]]; then
    AHEAD="$(git rev-list origin/${GIT_BRANCH}..HEAD --count)"
    if [[ "${AHEAD}" -gt 0 ]]; then
        echo "WARNING: Local is ${AHEAD} commit(s) ahead of origin/${GIT_BRANCH}."
        echo "         Run 'git push' after deploy to keep remote in sync."
    fi
fi

# ── Build ────────────────────────────────────────────────────────────

echo ""
echo "══════════════════════════════════════════════════════════"
echo "  DEPLOY: ${APP_NAME} → ${ENV_NAME}"
echo "  Commit: ${GIT_HASH} (${GIT_BRANCH})"
echo "══════════════════════════════════════════════════════════"
echo ""
echo "[1/7] Building backend..."
cd "${BACKEND_DIR}"
mvn clean package -DskipTests -q

readonly WAR_FILE="${BACKEND_DIR}/target/ROOT.war"
if [[ ! -f "${WAR_FILE}" ]]; then
    echo "ERROR: ROOT.war not found at ${WAR_FILE}"
    exit 1
fi
echo "       ROOT.war OK ($(du -h "${WAR_FILE}" | cut -f1))"

readonly PLATFORM_CONF="${BACKEND_DIR}/.platform/nginx/conf.d/timeout.conf"
if [[ ! -f "${PLATFORM_CONF}" ]]; then
    echo "ERROR: .platform/nginx/conf.d/timeout.conf not found"
    exit 1
fi
echo "       .platform/nginx/conf.d/timeout.conf OK"

# ── Create deployment ZIP ────────────────────────────────────────────

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
VERSION_LABEL="journeys-${ENV_ARG}-${TIMESTAMP}-${GIT_HASH}"
ZIP_FILE="/tmp/${VERSION_LABEL}.zip"

echo ""
echo "[2/7] Creating deployment ZIP..."
cd "${BACKEND_DIR}"
rm -f "${ZIP_FILE}"
zip -j "${ZIP_FILE}" target/ROOT.war -q
zip -r "${ZIP_FILE}" .platform/ -q
echo "       ${ZIP_FILE} OK"

# ── Upload to S3 ────────────────────────────────────────────────────

S3_KEY="${S3_PREFIX}/${VERSION_LABEL}.zip"

echo ""
echo "[3/7] Uploading to s3://${S3_BUCKET}/${S3_KEY}..."
aws s3 cp "${ZIP_FILE}" "s3://${S3_BUCKET}/${S3_KEY}" --region "${REGION}" --only-show-errors
echo "       Upload complete"

# ── Create EB application version ────────────────────────────────────

echo ""
echo "[4/7] Creating EB version: ${VERSION_LABEL}..."
aws elasticbeanstalk create-application-version \
    --application-name "${APP_NAME}" \
    --version-label "${VERSION_LABEL}" \
    --source-bundle "S3Bucket=${S3_BUCKET},S3Key=${S3_KEY}" \
    --region "${REGION}" \
    --output json > /dev/null
echo "       Version created"

# ── Deploy to environment ────────────────────────────────────────────

echo ""
echo "[5/7] Deploying ${VERSION_LABEL} → ${ENV_NAME}..."
aws elasticbeanstalk update-environment \
    --application-name "${APP_NAME}" \
    --environment-name "${ENV_NAME}" \
    --version-label "${VERSION_LABEL}" \
    --region "${REGION}" \
    --output json > /dev/null

# ── Wait for Green/Ready ────────────────────────────────────────────

echo ""
echo "[6/7] Waiting for ${ENV_NAME} to become Green/Ready..."
MAX_WAIT=300
ELAPSED=0
INTERVAL=15

while [[ ${ELAPSED} -lt ${MAX_WAIT} ]]; do
    sleep "${INTERVAL}"
    ELAPSED=$((ELAPSED + INTERVAL))

    STATUS=$(aws elasticbeanstalk describe-environments \
        --application-name "${APP_NAME}" \
        --environment-names "${ENV_NAME}" \
        --region "${REGION}" \
        --output json | python3 -c "import sys,json; e=json.load(sys.stdin)['Environments'][0]; print(e['Status'] + '|' + e['Health'])")

    ENV_STATUS="${STATUS%%|*}"
    ENV_HEALTH="${STATUS##*|}"

    echo "       ${ELAPSED}s — Status: ${ENV_STATUS}, Health: ${ENV_HEALTH}"

    if [[ "${ENV_STATUS}" == "Ready" && "${ENV_HEALTH}" == "Green" ]]; then
        break
    fi
done

if [[ "${ENV_STATUS}" != "Ready" || "${ENV_HEALTH}" != "Green" ]]; then
    echo ""
    echo "WARNING: Environment did not reach Green/Ready within ${MAX_WAIT}s"
    echo "         Status: ${ENV_STATUS}, Health: ${ENV_HEALTH}"
    echo "         Check AWS console manually."
    exit 1
fi

# ── Git tag ─────────────────────────────────────────────────────────

echo ""
echo "[7/7] Creating git tag..."
cd "${SCRIPT_DIR}"
TAG_NAME="deploy/journeys-${ENV_ARG}/${TIMESTAMP}"
git tag -a "${TAG_NAME}" -m "Deploy journeys ${ENV_ARG}: ${VERSION_LABEL}" 2>/dev/null && \
    echo "       Tagged: ${TAG_NAME}" && \
    git push origin "${TAG_NAME}" --quiet 2>/dev/null && \
    echo "       Pushed tag to origin" || \
    echo "       WARNING: Could not create/push tag (non-fatal)"

# ── Cleanup ──────────────────────────────────────────────────────────

rm -f "${ZIP_FILE}"

# ── Done ─────────────────────────────────────────────────────────────

echo ""
echo "══════════════════════════════════════════════════════════"
echo "  DEPLOYED SUCCESSFULLY"
echo ""
echo "  Application:  ${APP_NAME}"
echo "  Environment:  ${ENV_NAME}"
echo "  Version:      ${VERSION_LABEL}"
echo "  Commit:       ${GIT_HASH} (${GIT_BRANCH})"
echo "  Tag:          ${TAG_NAME}"
echo "  Status:       Green / Ready"
echo "══════════════════════════════════════════════════════════"
