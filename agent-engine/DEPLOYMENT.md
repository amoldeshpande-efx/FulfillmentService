# FulfillmentService — Deployment & Agent Engine Registration Guide

## Prerequisites

### Required IAM Roles
The deploying user (or CI/CD service account) needs:

| Role | Purpose |
|------|---------||
| `roles/artifactregistry.writer` | Push Docker images |
| `roles/run.developer` | Deploy Cloud Run services |
| `roles/iam.serviceAccountCreator` | Create runtime service account |
| `roles/aiplatform.user` | Register with Agent Engine |
| `roles/pubsub.editor` | Create topics/subscriptions |
| `roles/datastore.user` | Read/write Datastore entities |

### Required APIs
```
run.googleapis.com
artifactregistry.googleapis.com
aiplatform.googleapis.com
datastore.googleapis.com
pubsub.googleapis.com
cloudbuild.googleapis.com
```

---

## Option A: Automated Deployment (Recommended)

```bash
cd /Users/axd388/Desktop/GIT-SOURCES/FulfillmentService

# Set environment
export GCP_PROJECT_ID=ews-vs-icie-dev-npe-0bb5
export GCP_REGION=us-central1

# Run the deployment script
chmod +x agent-engine/deploy-agent-engine.sh
./agent-engine/deploy-agent-engine.sh
```

The script will:
1. Verify APIs are enabled
2. Create a dedicated service account with least-privilege roles
3. Create an Artifact Registry Docker repository
4. Build and push the Docker image
5. Deploy to Cloud Run
6. Verify health and MCP endpoints
7. Register with Vertex AI Agent Engine

---

## Option B: Manual Step-by-Step Deployment

### Step 1: Create Artifact Registry Repository
```bash
gcloud artifacts repositories create fulfillment-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="Fulfillment Agent Docker images" \
  --project=ews-vs-icie-dev-npe-0bb5
```

### Step 2: Build and Push Docker Image
```bash
# Configure Docker auth
gcloud auth configure-docker us-central1-docker.pkg.dev --quiet

# Build
docker build -t us-central1-docker.pkg.dev/ews-vs-icie-dev-npe-0bb5/fulfillment-repo/fulfillment-agent:latest .

# Push
docker push us-central1-docker.pkg.dev/ews-vs-icie-dev-npe-0bb5/fulfillment-repo/fulfillment-agent:latest
```

Or use Cloud Build:
```bash
gcloud builds submit \
  --tag=us-central1-docker.pkg.dev/ews-vs-icie-dev-npe-0bb5/fulfillment-repo/fulfillment-agent:latest \
  --project=ews-vs-icie-dev-npe-0bb5
```

### Step 3: Create Runtime Service Account
```bash
gcloud iam service-accounts create fulfillment-agent-sa \
  --display-name="Fulfillment Agent Service Account" \
  --project=ews-vs-icie-dev-npe-0bb5

# Grant roles
for role in roles/datastore.user roles/pubsub.publisher roles/logging.logWriter roles/monitoring.metricWriter; do
  gcloud projects add-iam-policy-binding ews-vs-icie-dev-npe-0bb5 \
    --member="serviceAccount:fulfillment-agent-sa@ews-vs-icie-dev-npe-0bb5.iam.gserviceaccount.com" \
    --role="$role" --quiet
done
```

### Step 4: Deploy to Cloud Run
```bash
gcloud run deploy fulfillment-agent \
  --image=us-central1-docker.pkg.dev/ews-vs-icie-dev-npe-0bb5/fulfillment-repo/fulfillment-agent:latest \
  --region=us-central1 \
  --project=ews-vs-icie-dev-npe-0bb5 \
  --service-account=fulfillment-agent-sa@ews-vs-icie-dev-npe-0bb5.iam.gserviceaccount.com \
  --platform=managed \
  --port=8080 \
  --memory=1Gi \
  --cpu=1 \
  --min-instances=0 \
  --max-instances=10 \
  --timeout=300 \
  --set-env-vars="GCP_PROJECT_ID=ews-vs-icie-dev-npe-0bb5,PUBSUB_TOPIC=fulfillment-request-events,DATASTORE_KIND=RequestEntry" \
  --allow-unauthenticated
```

### Step 5: Verify Deployment
```bash
# Get Cloud Run URL
SERVICE_URL=$(gcloud run services describe fulfillment-agent \
  --region=us-central1 --format="value(status.url)")

# Health check
curl -s "${SERVICE_URL}/actuator/health" | python3 -m json.tool

# Swagger UI
echo "Swagger: ${SERVICE_URL}/swagger-ui.html"

# MCP Initialize
curl -s -X POST "${SERVICE_URL}/mcp" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}'

# MCP List Tools
curl -s -X POST "${SERVICE_URL}/mcp" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

### Step 6: Register with Agent Engine
```bash
# Update the agent config with the actual Cloud Run URL
SERVICE_URL=$(gcloud run services describe fulfillment-agent \
  --region=us-central1 --format="value(status.url)")

sed "s|https://fulfillment-agent-HASH.run.app|${SERVICE_URL}|g" \
  agent-engine/agent-config.json > /tmp/agent-config-live.json

# Register via Vertex AI API
curl -X POST \
  "https://us-central1-aiplatform.googleapis.com/v1beta1/projects/ews-vs-icie-dev-npe-0bb5/locations/us-central1/reasoningEngines" \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d @/tmp/agent-config-live.json
```

---

## Endpoints After Deployment

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Health check |
| `/swagger-ui.html` | GET | Swagger UI |
| `/api/v1/fulfillment/request` | POST | REST fulfillment endpoint |
| `/mcp` | POST | MCP Streamable HTTP (JSON-RPC 2.0) |
| `/mcp/sse` | GET | MCP SSE transport |
| `/mcp/message` | POST | MCP SSE message endpoint |
| `/.well-known/agent.json` | GET | A2A Agent Card (serve agent-card.json) |

---

## Troubleshooting

### Permission Denied on API Enable
```bash
# Request your platform team to run:
gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
  --project=ews-vs-icie-dev-npe-0bb5
```

### Permission Denied on Artifact Registry
```bash
# Need: roles/artifactregistry.writer or roles/artifactregistry.admin
gcloud projects add-iam-policy-binding ews-vs-icie-dev-npe-0bb5 \
  --member="user:amol.deshpande@equifax.com" \
  --role="roles/artifactregistry.writer"
```

### Cloud Run Deploy Fails
```bash
# Verify run.googleapis.com is enabled
gcloud services list --enabled --filter="config.name:run.googleapis.com"

# Need: roles/run.developer
gcloud projects add-iam-policy-binding ews-vs-icie-dev-npe-0bb5 \
  --member="user:amol.deshpande@equifax.com" \
  --role="roles/run.developer"
```