#!/bin/bash
# =============================================================================
# deploy-cloud-function.sh
# Deploys FulfillmentService as Cloud Function Gen 1 in us-east1
# =============================================================================
set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:-ews-vs-icie-dev-npe-0bb5}"
REGION="${GCP_REGION:-us-east1}"
FUNCTION_NAME="fulfillment-agent"
ENTRY_POINT="com.equifax.ews.vs.ici.FulfillmentFunction"
SA_EMAIL="fulfillment-agent-sa@${PROJECT_ID}.iam.gserviceaccount.com"
VPC_CONNECTOR="projects/efx-gcp-ews-svpc-npe-6787/locations/${REGION}/connectors/ews-vs-icie-dev-cn-2"

echo "═══════════════════════════════════════════════════════════"
echo "  FulfillmentService → Cloud Function Gen 1"
echo "═══════════════════════════════════════════════════════════"
echo "  Project:  ${PROJECT_ID}"
echo "  Region:   ${REGION}"
echo "  Function: ${FUNCTION_NAME}"
echo "  SA:       ${SA_EMAIL}"
echo "═══════════════════════════════════════════════════════════"

# ── Step 1: Build ─────────────────────────────────────────────────────────────
echo ""
echo "▶ Step 1: Building with Maven (cloud-function profile, Java 17 target)..."
mvn clean package -Pcloud-function -DskipTests -q

JAR_COUNT=$(ls target/deploy/*.jar 2>/dev/null | wc -l)
echo "  ✓ Built ${JAR_COUNT} JARs in target/deploy/"

# ── Step 2: Deploy ────────────────────────────────────────────────────────────
echo ""
echo "▶ Step 2: Deploying Cloud Function..."

gcloud functions deploy "${FUNCTION_NAME}" \
  --no-gen2 \
  --runtime=java17 \
  --trigger-http \
  --allow-unauthenticated \
  --entry-point="${ENTRY_POINT}" \
  --region="${REGION}" \
  --project="${PROJECT_ID}" \
  --memory=2048MB \
  --timeout=540s \
  --service-account="${SA_EMAIL}" \
  --vpc-connector="${VPC_CONNECTOR}" \
  --egress-settings=all \
  --ingress-settings=internal-only \
  --set-env-vars="GCP_PROJECT_ID=${PROJECT_ID},PUBSUB_TOPIC=fulfillment-request-events,DATASTORE_KIND=RequestEntry" \
  --source=target/deploy

# ── Step 3: Get URL and verify ───────────────────────────────────────────────
echo ""
echo "▶ Step 3: Verifying deployment..."

FUNCTION_URL=$(gcloud functions describe "${FUNCTION_NAME}" \
  --region="${REGION}" \
  --project="${PROJECT_ID}" \
  --format="value(httpsTrigger.url)" 2>/dev/null)

echo "  Function URL: ${FUNCTION_URL}"

echo ""
echo "  Testing health endpoint..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${FUNCTION_URL}/actuator/health" --max-time 60 2>/dev/null || echo "timeout")
if [ "${HTTP_STATUS}" = "200" ]; then
  echo "  ✓ Health check passed (HTTP 200)"
else
  echo "  ⚠ Health check returned: ${HTTP_STATUS} (first call may cold-start ~15-20s)"
  echo "  Retrying..."
  sleep 20
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${FUNCTION_URL}/actuator/health" --max-time 60 2>/dev/null || echo "timeout")
  echo "  Retry result: HTTP ${HTTP_STATUS}"
fi

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ✅ DEPLOYMENT COMPLETE"
echo "═══════════════════════════════════════════════════════════"
echo "  Function URL:   ${FUNCTION_URL}"
echo "  REST Endpoint:  ${FUNCTION_URL}/api/v1/fulfillment/request"
echo "  Swagger UI:     ${FUNCTION_URL}/swagger-ui.html"
echo "  MCP Endpoint:   ${FUNCTION_URL}/mcp"
echo "  MCP SSE:        ${FUNCTION_URL}/mcp/sse"
echo "  Health:         ${FUNCTION_URL}/actuator/health"
echo "═══════════════════════════════════════════════════════════"