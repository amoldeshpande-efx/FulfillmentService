#!/bin/bash
# =============================================================================
# deploy-agent-engine.sh
# Deploys FulfillmentService to Cloud Run and registers with Vertex AI Agent Engine
# =============================================================================
set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────
PROJECT_ID="${GCP_PROJECT_ID:-ews-vs-icie-dev-npe-0bb5}"
REGION="${GCP_REGION:-us-central1}"
SERVICE_NAME="fulfillment-agent"
AR_REPO="fulfillment-repo"
IMAGE_TAG="latest"
IMAGE_URI="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/${SERVICE_NAME}:${IMAGE_TAG}"
SA_NAME="${SERVICE_NAME}-sa"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
AGENT_CONFIG="agent-engine/agent-config.json"

echo "═══════════════════════════════════════════════════════════"
echo "  FulfillmentService → Agent Engine Deployment"
echo "═══════════════════════════════════════════════════════════"
echo "  Project:  ${PROJECT_ID}"
echo "  Region:   ${REGION}"
echo "  Image:    ${IMAGE_URI}"
echo "═══════════════════════════════════════════════════════════"

# ── Step 1: Verify prerequisites ──────────────────────────────────────────────
echo ""
echo "▶ Step 1: Verifying prerequisites..."

gcloud config set project "${PROJECT_ID}" --quiet
gcloud config set compute/region "${REGION}" --quiet

# Check required APIs
REQUIRED_APIS=(
  "run.googleapis.com"
  "artifactregistry.googleapis.com"
  "aiplatform.googleapis.com"
  "datastore.googleapis.com"
  "pubsub.googleapis.com"
)

echo "  Checking enabled APIs..."
ENABLED_APIS=$(gcloud services list --enabled --format="value(config.name)" 2>/dev/null)
MISSING_APIS=()
for api in "${REQUIRED_APIS[@]}"; do
  if echo "${ENABLED_APIS}" | grep -q "^${api}$"; then
    echo "  ✓ ${api}"
  else
    echo "  ✗ ${api} (NOT ENABLED)"
    MISSING_APIS+=("${api}")
  fi
done

if [ ${#MISSING_APIS[@]} -gt 0 ]; then
  echo ""
  echo "  ⚠ Missing APIs. Attempting to enable..."
  gcloud services enable "${MISSING_APIS[@]}" --quiet 2>/dev/null || {
    echo "  ✗ Could not enable APIs. Request your platform team to enable:"
    printf '    - %s\n' "${MISSING_APIS[@]}"
    echo ""
    echo "  Run: gcloud services enable ${MISSING_APIS[*]}"
    exit 1
  }
fi

# ── Step 2: Create service account ────────────────────────────────────────────
echo ""
echo "▶ Step 2: Creating service account..."

if gcloud iam service-accounts describe "${SA_EMAIL}" --project="${PROJECT_ID}" &>/dev/null; then
  echo "  ✓ Service account ${SA_EMAIL} already exists"
else
  gcloud iam service-accounts create "${SA_NAME}" \
    --display-name="Fulfillment Agent Service Account" \
    --project="${PROJECT_ID}" || {
    echo "  ✗ Cannot create service account. Request your platform team to create:"
    echo "    gcloud iam service-accounts create ${SA_NAME} \\"
    echo "      --display-name='Fulfillment Agent Service Account' \\"
    echo "      --project=${PROJECT_ID}"
    exit 1
  }
  echo "  ✓ Created ${SA_EMAIL}"
fi

# Grant required roles to service account
SA_ROLES=(
  "roles/datastore.user"
  "roles/pubsub.publisher"
  "roles/logging.logWriter"
  "roles/monitoring.metricWriter"
)

echo "  Binding IAM roles..."
for role in "${SA_ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="${role}" \
    --quiet --no-user-output-enabled 2>/dev/null || true
  echo "  ✓ ${role}"
done

# ── Step 3: Create Artifact Registry repository ──────────────────────────────
echo ""
echo "▶ Step 3: Creating Artifact Registry repository..."

if gcloud artifacts repositories describe "${AR_REPO}" \
    --location="${REGION}" --project="${PROJECT_ID}" &>/dev/null; then
  echo "  ✓ Repository ${AR_REPO} already exists"
else
  gcloud artifacts repositories create "${AR_REPO}" \
    --repository-format=docker \
    --location="${REGION}" \
    --description="Fulfillment Agent Docker images" \
    --project="${PROJECT_ID}" || {
    echo "  ✗ Cannot create AR repo. Request your platform team to create:"
    echo "    gcloud artifacts repositories create ${AR_REPO} \\"
    echo "      --repository-format=docker --location=${REGION} \\"
    echo "      --project=${PROJECT_ID}"
    exit 1
  }
  echo "  ✓ Created ${AR_REPO}"
fi

# ── Step 4: Build and push Docker image ────────────────────────────────────────
echo ""
echo "▶ Step 4: Building and pushing Docker image..."

# Configure Docker auth for Artifact Registry
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet

# Build with Cloud Build (or local Docker)
if command -v docker &>/dev/null; then
  echo "  Building with local Docker..."
  docker build -t "${IMAGE_URI}" .
  docker push "${IMAGE_URI}"
else
  echo "  Building with Cloud Build..."
  gcloud builds submit \
    --tag="${IMAGE_URI}" \
    --project="${PROJECT_ID}" \
    --quiet
fi
echo "  ✓ Image pushed: ${IMAGE_URI}"

# ── Step 5: Deploy to Cloud Run ───────────────────────────────────────────────
echo ""
echo "▶ Step 5: Deploying to Cloud Run..."

gcloud run deploy "${SERVICE_NAME}" \
  --image="${IMAGE_URI}" \
  --region="${REGION}" \
  --project="${PROJECT_ID}" \
  --service-account="${SA_EMAIL}" \
  --platform=managed \
  --port=8080 \
  --memory=1Gi \
  --cpu=1 \
  --min-instances=0 \
  --max-instances=10 \
  --timeout=300 \
  --set-env-vars="GCP_PROJECT_ID=${PROJECT_ID},PUBSUB_TOPIC=fulfillment-request-events,DATASTORE_KIND=RequestEntry,JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC" \
  --allow-unauthenticated \
  --quiet

# Get the Cloud Run URL
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" \
  --region="${REGION}" \
  --project="${PROJECT_ID}" \
  --format="value(status.url)")

echo "  ✓ Deployed: ${SERVICE_URL}"

# ── Step 6: Verify deployment ────────────────────────────────────────────────
echo ""
echo "▶ Step 6: Verifying deployment..."

echo "  Testing health endpoint..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${SERVICE_URL}/actuator/health" --max-time 30)
if [ "${HTTP_STATUS}" = "200" ]; then
  echo "  ✓ Health check passed (HTTP 200)"
else
  echo "  ⚠ Health check returned HTTP ${HTTP_STATUS}"
fi

echo "  Testing MCP initialize..."
MCP_RESPONSE=$(curl -s -X POST "${SERVICE_URL}/mcp" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}' \
  --max-time 10)
echo "  MCP Response: ${MCP_RESPONSE}" | head -c 200

# ── Step 7: Register with Agent Engine ───────────────────────────────────────
echo ""
echo "▶ Step 7: Registering with Vertex AI Agent Engine..."

# Update agent config with actual Cloud Run URL
AGENT_CONFIG_UPDATED=$(mktemp)
sed "s|https://fulfillment-agent-HASH.run.app|${SERVICE_URL}|g" "${AGENT_CONFIG}" > "${AGENT_CONFIG_UPDATED}"

echo "  Creating Agent Engine reasoning engine..."

# Use Vertex AI API to create the agent
AGENT_RESPONSE=$(curl -s -X POST \
  "https://${REGION}-aiplatform.googleapis.com/v1beta1/projects/${PROJECT_ID}/locations/${REGION}/reasoningEngines" \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d @"${AGENT_CONFIG_UPDATED}")

rm -f "${AGENT_CONFIG_UPDATED}"

AGENT_NAME=$(echo "${AGENT_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))" 2>/dev/null || echo "")

if [ -n "${AGENT_NAME}" ] && [ "${AGENT_NAME}" != "" ]; then
  echo "  ✓ Agent registered: ${AGENT_NAME}"
  AGENT_ID=$(echo "${AGENT_NAME}" | awk -F'/' '{print $NF}')
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  echo "  ✅ DEPLOYMENT COMPLETE"
  echo "═══════════════════════════════════════════════════════════"
  echo "  Cloud Run URL:  ${SERVICE_URL}"
  echo "  Swagger UI:     ${SERVICE_URL}/swagger-ui.html"
  echo "  MCP Endpoint:   ${SERVICE_URL}/mcp"
  echo "  Agent Engine ID: ${AGENT_ID}"
  echo "  Agent Name:     ${AGENT_NAME}"
  echo "═══════════════════════════════════════════════════════════"
else
  echo "  ⚠ Agent Engine registration response:"
  echo "${AGENT_RESPONSE}" | python3 -m json.tool 2>/dev/null || echo "${AGENT_RESPONSE}"
  echo ""
  echo "  If registration failed due to permissions, request:"
  echo "    roles/aiplatform.admin or roles/aiplatform.user"
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  echo "  ⚠ PARTIAL DEPLOYMENT"
  echo "═══════════════════════════════════════════════════════════"
  echo "  Cloud Run URL:  ${SERVICE_URL}"
  echo "  Swagger UI:     ${SERVICE_URL}/swagger-ui.html"
  echo "  MCP Endpoint:   ${SERVICE_URL}/mcp"
  echo "  Agent Engine:   REGISTRATION PENDING"
  echo "═══════════════════════════════════════════════════════════"
fi