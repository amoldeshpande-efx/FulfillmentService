#!/bin/bash
# setup-gcp.sh — Create GCP resources for FulfillmentService deployment
# Usage: ./setup-gcp.sh <PROJECT_ID> <REGION>

set -euo pipefail

PROJECT_ID=${1:?"Usage: $0 <PROJECT_ID> <REGION>"}
REGION=${2:-us-central1}
SERVICE_NAME="fulfillment-service"
SA_NAME="fulfillment-sa"
TOPIC="fulfillment-request-events"
SUBSCRIPTION="fulfillment-request-subscription"
ARTIFACT_REPO="fulfillment-repo"

echo "=== Setting up GCP resources for ${SERVICE_NAME} ==="
echo "Project: ${PROJECT_ID}, Region: ${REGION}"

# Enable required APIs
echo "Enabling APIs..."
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  datastore.googleapis.com \
  pubsub.googleapis.com \
  --project="${PROJECT_ID}"

# Create Artifact Registry repository
echo "Creating Artifact Registry repository..."
gcloud artifacts repositories create "${ARTIFACT_REPO}" \
  --repository-format=docker \
  --location="${REGION}" \
  --project="${PROJECT_ID}" \
  --description="Docker images for fulfillment services" \
  2>/dev/null || echo "Repository already exists"

# Create Pub/Sub topic and subscription
echo "Creating Pub/Sub topic and subscription..."
gcloud pubsub topics create "${TOPIC}" \
  --project="${PROJECT_ID}" \
  2>/dev/null || echo "Topic already exists"

gcloud pubsub subscriptions create "${SUBSCRIPTION}" \
  --topic="${TOPIC}" \
  --ack-deadline=60 \
  --project="${PROJECT_ID}" \
  2>/dev/null || echo "Subscription already exists"

# Create service account
echo "Creating service account..."
gcloud iam service-accounts create "${SA_NAME}" \
  --display-name="Fulfillment Service Account" \
  --project="${PROJECT_ID}" \
  2>/dev/null || echo "Service account already exists"

SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

# Grant roles to service account
echo "Granting IAM roles..."
ROLES=(
  "roles/datastore.user"
  "roles/pubsub.publisher"
  "roles/pubsub.subscriber"
  "roles/logging.logWriter"
  "roles/monitoring.metricWriter"
)

for ROLE in "${ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="${ROLE}" \
    --quiet
done

echo ""
echo "=== Setup complete ==="
echo "Service Account: ${SA_EMAIL}"
echo "Pub/Sub Topic:   ${TOPIC}"
echo "Artifact Repo:   ${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPO}"
echo ""
echo "Next steps:"
echo "  1. Build: gcloud builds submit --config=cloudbuild.yaml --project=${PROJECT_ID}"
echo "  2. Or deploy locally: docker build -t ${SERVICE_NAME} . && docker run -p 8080:8080 ${SERVICE_NAME}"