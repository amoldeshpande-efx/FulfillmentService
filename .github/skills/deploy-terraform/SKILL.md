---
name: deploy-terraform
description: 'Deploy FulfillmentService via Terraform CI/CD pipeline. Use when: deploying to GCP, updating Cloud Function configuration, modifying Terraform main.tf, preparing deployment artifacts, checking deployment status.'
argument-hint: 'What to deploy or change (e.g., "update memory to 4GB", "add new env var", "full deployment")'
---

# Deploy via Terraform

## When to Use

- Deploying FulfillmentService to GCP project `ews-vs-icie-dev-npe-0bb5`
- Updating Cloud Function configuration (memory, timeout, env vars)
- Modifying infrastructure (IAM, storage, VPC settings)

## Important Context

- **Human users cannot deploy directly** — `iam.serviceAccounts.actAs` is not granted to human accounts
- All deployments go through the **Terraform CI/CD pipeline** using SA `tf-ews-vs-icie-dev-npe`
- The pipeline runs on GKE workload identity from project `eops-cicd-mbps-npe-497a`
- Terraform config: `terraform/main.tf`

## Files to Modify

1. **Terraform config** — `terraform/main.tf`
2. **Deployment scripts** — `agent-engine/deploy-cloud-function.sh` (reference)
3. **Agent descriptors** — `agent-engine/agent-config.json`, `agent-engine/agent-card.json`

## Procedure

### Step 1: Build the Cloud Function JAR

```bash
mvn clean package -DskipTests -P cloud-function
```

This uses the `cloud-function` Maven profile which targets Java 17 and includes the `functions-framework-api`.

### Step 2: Create Deployment ZIP

```bash
cd target
cp FulfillmentService-1.0-SNAPSHOT.jar deploy.jar
zip deploy.zip deploy.jar
cd ..
```

### Step 3: Update Terraform Config (if needed)

Edit `terraform/main.tf`. Key sections:

| Section | What to change |
|---------|---------------|
| `google_cloudfunctions_function.fulfillment_agent` | Memory, timeout, runtime, env vars |
| `environment_variables` | Add/modify environment variables |
| `variable` blocks | Change project ID, region, VPC connector |
| `google_project_iam_member` | Add new IAM roles for the service account |

### Step 4: Validate Terraform

```bash
cd terraform
terraform init
terraform validate
terraform plan -out=tfplan
```

Review the plan output carefully before proceeding.

### Step 5: Submit to CI/CD Pipeline

This service's Terraform must be applied through the team's CI/CD pipeline:

1. Commit `terraform/main.tf` changes to the IaC repository
2. Pipeline SA `tf-ews-vs-icie-dev-npe` executes `terraform apply`
3. Verify deployment via:
   ```bash
   gcloud functions describe fulfillment-agent --region=us-east1 --format="table(status,updateTime,httpsTrigger.url)"
   ```

### Step 6: Verify Deployment

```bash
# Check function status
gcloud functions describe fulfillment-agent --region=us-east1

# Test MCP endpoint
curl -s -X POST https://<FUNCTION_URL>/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}'

# Test REST endpoint
curl -s -X POST https://<FUNCTION_URL>/api/v1/fulfillment/request \
  -H "Content-Type: application/json" \
  -d '{"customerId":"TEST","orderId":"TEST","requestType":"VERIFICATION","payload":{},"userEmail":"test@equifax.com","correlationId":"deploy-test"}'
```

## Key Configuration in main.tf

- **Runtime:** `java17` (Cloud Function adapter requirement)
- **Memory:** `2048` MB
- **Timeout:** `540` seconds
- **SA:** `ici-fulfillmentservice-dev-gsa`
- **VPC Connector:** `ews-vs-icie-dev-cn-2` (required by org policy)
- **Ingress:** `ALLOW_INTERNAL_ONLY` (required by org policy)
- **Egress:** `ALL_TRAFFIC` (required by org policy)
- **Labels:** Must include `capex_project_code`, `cmdb_bus_svc_id`, `cost_center`, `data_class`, `division`

## Permission Request

If `iam.serviceAccounts.actAs` is needed for direct deployment, the prepared request is at `agent-engine/PERMISSION-REQUEST.md`.