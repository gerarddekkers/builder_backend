#!/usr/bin/env bash
set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════
# deploy.sh — Unified deployment for Assessment Builder & Learning Journeys
# ═══════════════════════════════════════════════════════════════════════
#
# Usage: ./deploy.sh journeys   test
#        ./deploy.sh journeys   prod
#        ./deploy.sh assessment test
#        ./deploy.sh assessment prod
#
# EB Applications:
#   journeys   → journeys-builder  (journeys-test, journeys-prod)
#   assessment → metro-builder     (Metro-builder-env, metro-builder-prod)
#
# S3 prefixes:
#   journeys   → journeys/
#   assessment → builder/
# ═══════════════════════════════════════════════════════════════════════

readonly S3_BUCKET="elasticbeanstalk-eu-west-1-643502197318"
readonly REGION="eu-west-1"
readonly SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly BACKEND_DIR="${SCRIPT_DIR}/backend"

ZIP_FILE=""

cleanup() {
    if [[ -n "${ZIP_FILE}" && -f "${ZIP_FILE}" ]]; then
        rm -f "${ZIP_FILE}"
    fi
}
trap cleanup EXIT

# ── Usage ──────────────────────────────────────────────────────────────

usage() {
    echo "Usage: $0 <application> <environment>"
    echo ""
    echo "  application:  journeys | assessment"
    echo "  environment:  test | prod"
    echo ""
    echo "Examples:"
    echo "  $0 journeys test"
    echo "  $0 journeys prod"
    echo "  $0 assessment test"
    echo "  $0 assessment prod"
    exit 1
}

# ── Argument validation ───────────────────────────────────────────────

if [[ $# -ne 2 ]]; then
    echo "ERROR: Exactly two arguments required."
    usage
fi

APP_ARG="$1"
ENV_ARG="$2"

# Validate application
case "${APP_ARG}" in
    journeys|assessment) ;;
    *)
        echo "ERROR: Invalid application '${APP_ARG}'. Must be 'journeys' or 'assessment'."
        usage
        ;;
esac

# Validate environment
case "${ENV_ARG}" in
    test|prod) ;;
    *)
        echo "ERROR: Invalid environment '${ENV_ARG}'. Must be 'test' or 'prod'."
        usage
        ;;
esac

# ── Resolve deployment targets ────────────────────────────────────────

if [[ "${APP_ARG}" == "journeys" ]]; then
    APP_NAME="journeys-builder"
    S3_PREFIX="journeys"
    if [[ "${ENV_ARG}" == "test" ]]; then
        ENV_NAME="journeys-test"
    else
        ENV_NAME="journeys-prod"
    fi
elif [[ "${APP_ARG}" == "assessment" ]]; then
    APP_NAME="metro-builder"
    S3_PREFIX="builder"
    if [[ "${ENV_ARG}" == "test" ]]; then
        ENV_NAME="Metro-builder-env"
    else
        ENV_NAME="metro-builder-prod"
    fi
fi

VERSION_LABEL="${APP_ARG}-${ENV_ARG}-$(date +%Y%m%d-%H%M%S)"
S3_KEY="${S3_PREFIX}/${VERSION_LABEL}.zip"

# ── Cross-check: S3 prefix matches application ───────────────────────

if [[ "${APP_ARG}" == "journeys" && "${S3_PREFIX}" != "journeys" ]]; then
    echo "FATAL: S3 prefix mismatch — journeys must use 'journeys/' prefix, got '${S3_PREFIX}/'"
    exit 1
fi
if [[ "${APP_ARG}" == "assessment" && "${S3_PREFIX}" != "builder" ]]; then
    echo "FATAL: S3 prefix mismatch — assessment must use 'builder/' prefix, got '${S3_PREFIX}/'"
    exit 1
fi

# ── Prerequisite checks ──────────────────────────────────────────────

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

# ── Production safety gate ───────────────────────────────────────────

if [[ "${ENV_ARG}" == "prod" ]]; then
    echo ""
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║             ⚠  PRODUCTION DEPLOYMENT  ⚠                ║"
    echo "╠══════════════════════════════════════════════════════════╣"
    echo "║                                                        ║"
    printf "║  Application:   %-38s ║\n" "${APP_NAME}"
    printf "║  Environment:   %-38s ║\n" "${ENV_NAME}"
    printf "║  Version:       %-38s ║\n" "${VERSION_LABEL}"
    printf "║  S3 key:        %-38s ║\n" "${S3_KEY}"
    echo "║                                                        ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""
    read -rp "Type DEPLOY to continue, anything else to abort: " CONFIRM
    if [[ "${CONFIRM}" != "DEPLOY" ]]; then
        echo "Aborted."
        exit 1
    fi
    echo ""
fi

# ── Build ────────────────────────────────────────────────────────────

echo "══════════════════════════════════════════════════════════"
echo "  DEPLOY: ${APP_NAME} → ${ENV_NAME}"
echo "══════════════════════════════════════════════════════════"
echo ""
echo "[1/6] Building backend..."
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

ZIP_FILE="/tmp/${VERSION_LABEL}.zip"

echo ""
echo "[2/6] Creating deployment ZIP..."
cd "${BACKEND_DIR}"
rm -f "${ZIP_FILE}"
zip -j "${ZIP_FILE}" target/ROOT.war -q
zip -r "${ZIP_FILE}" .platform/ -q
echo "       ${ZIP_FILE} OK"

# ── Upload to S3 ────────────────────────────────────────────────────

echo ""
echo "[3/6] Uploading to s3://${S3_BUCKET}/${S3_KEY}..."
aws s3 cp "${ZIP_FILE}" "s3://${S3_BUCKET}/${S3_KEY}" --region "${REGION}" --only-show-errors
echo "       Upload complete"

# ── Create EB application version ────────────────────────────────────

echo ""
echo "[4/6] Creating EB version: ${VERSION_LABEL}..."
aws elasticbeanstalk create-application-version \
    --application-name "${APP_NAME}" \
    --version-label "${VERSION_LABEL}" \
    --source-bundle "S3Bucket=${S3_BUCKET},S3Key=${S3_KEY}" \
    --region "${REGION}" \
    --output json > /dev/null
echo "       Version created"

# ── Deploy to environment ────────────────────────────────────────────

echo ""
echo "[5/6] Deploying ${VERSION_LABEL} → ${ENV_NAME}..."
aws elasticbeanstalk update-environment \
    --application-name "${APP_NAME}" \
    --environment-name "${ENV_NAME}" \
    --version-label "${VERSION_LABEL}" \
    --region "${REGION}" \
    --output json > /dev/null

# ── Wait for Green/Ready ────────────────────────────────────────────

echo ""
echo "[6/6] Waiting for ${ENV_NAME} to become Green/Ready..."
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

# ── Done ─────────────────────────────────────────────────────────────

echo ""
echo "══════════════════════════════════════════════════════════"
echo "  SUCCESS"
echo ""
echo "  Application:  ${APP_NAME}"
echo "  Environment:  ${ENV_NAME}"
echo "  Version:      ${VERSION_LABEL}"
echo "  S3 key:       ${S3_KEY}"
echo "  Status:       Green / Ready"
echo "══════════════════════════════════════════════════════════"