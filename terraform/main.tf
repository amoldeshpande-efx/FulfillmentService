# =============================================================================
# Terraform config for deploying FulfillmentService as Cloud Function Gen 1
# Mirrors the pattern used by existing ICI functions (ICI-DatastoreExport-dev, etc.)
# Add this to your team's Terraform IaC repository
# =============================================================================

variable "project_id" {
  default = "ews-vs-icie-dev-npe-0bb5"
}

variable "region" {
  default = "us-east1"
}

variable "vpc_connector" {
  default = "projects/efx-gcp-ews-svpc-npe-6787/locations/us-east1/connectors/ews-vs-icie-dev-cn-2"
}

# ── Use the existing service account ─────────────────────────────────────────
# ici-fulfillmentservice-dev-gsa already exists in the project
data "google_service_account" "fulfillment_sa" {
  account_id = "ici-fulfillmentservice-dev-gsa"
  project    = var.project_id
}

# ── Grant actAs so Cloud Functions can use this SA ───────────────────────────
resource "google_service_account_iam_member" "cf_actas" {
  service_account_id = data.google_service_account.fulfillment_sa.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${data.google_service_account.fulfillment_sa.email}"
}

# ── Grant runtime permissions to the SA ──────────────────────────────────────
resource "google_project_iam_member" "datastore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${data.google_service_account.fulfillment_sa.email}"
}

resource "google_project_iam_member" "pubsub_publisher" {
  project = var.project_id
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${data.google_service_account.fulfillment_sa.email}"
}

resource "google_project_iam_member" "log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${data.google_service_account.fulfillment_sa.email}"
}

# ── Upload source to GCS ────────────────────────────────────────────────────
resource "google_storage_bucket" "function_source" {
  name     = "${var.project_id}-fulfillment-agent-source"
  location = var.region
  project  = var.project_id

  uniform_bucket_level_access = true

  labels = {
    capex_project_code = "122687"
    cmdb_bus_svc_id    = "asve0055613"
    cost_center        = "3601"
    data_class         = "2"
    division           = "0210"
  }
}

resource "google_storage_bucket_object" "function_source_zip" {
  name   = "fulfillment-agent-${filemd5("${path.module}/target/deploy.zip")}.zip"
  bucket = google_storage_bucket.function_source.name
  source = "${path.module}/target/deploy.zip"
}

# ── Cloud Function Gen 1 ────────────────────────────────────────────────────
resource "google_cloudfunctions_function" "fulfillment_agent" {
  name        = "fulfillment-agent"
  project     = var.project_id
  region      = var.region
  description = "Fulfillment Agent - REST + MCP endpoints for fulfillment request processing"
  runtime     = "java17"

  available_memory_mb   = 2048
  timeout               = 540
  entry_point           = "com.equifax.ews.vs.ici.FulfillmentFunction"
  trigger_http          = true

  source_archive_bucket = google_storage_bucket.function_source.name
  source_archive_object = google_storage_bucket_object.function_source_zip.name

  service_account_email = data.google_service_account.fulfillment_sa.email

  vpc_connector                 = var.vpc_connector
  vpc_connector_egress_settings = "ALL_TRAFFIC"
  ingress_settings              = "ALLOW_INTERNAL_ONLY"

  environment_variables = {
    GCP_PROJECT_ID = var.project_id
    PUBSUB_TOPIC   = "fulfillment-request-events"
    DATASTORE_KIND = "RequestEntry"
  }

  labels = {
    capex_project_code = "122687"
    cmdb_bus_svc_id    = "asve0055613"
    cost_center        = "3601"
    data_class         = "2"
    division           = "0210"
  }
}

# ── Allow unauthenticated (internal-only) invocations ────────────────────────
resource "google_cloudfunctions_function_iam_member" "invoker" {
  project        = var.project_id
  region         = var.region
  cloud_function = google_cloudfunctions_function.fulfillment_agent.name
  role           = "roles/cloudfunctions.invoker"
  member         = "allUsers"
}

# ── Outputs ──────────────────────────────────────────────────────────────────
output "function_url" {
  value = google_cloudfunctions_function.fulfillment_agent.https_trigger_url
}

output "rest_endpoint" {
  value = "${google_cloudfunctions_function.fulfillment_agent.https_trigger_url}/api/v1/fulfillment/request"
}

output "mcp_endpoint" {
  value = "${google_cloudfunctions_function.fulfillment_agent.https_trigger_url}/mcp"
}

output "swagger_url" {
  value = "${google_cloudfunctions_function.fulfillment_agent.https_trigger_url}/swagger-ui.html"
}