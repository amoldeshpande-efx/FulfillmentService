Subject: ONE Permission Needed — FulfillmentService Cloud Function Deployment (ews-vs-icie-dev-npe-0bb5)

Hi Platform Team,

I need **one IAM binding** to deploy the FulfillmentService as a Cloud Function
Gen 1 in **us-east1** on project **ews-vs-icie-dev-npe-0bb5**.

## What's Needed (Single Command)

Grant `roles/iam.serviceAccountUser` on the **already-existing** service account
`fulfillment-agent-sa` to my user:

```bash
gcloud iam service-accounts add-iam-policy-binding \
  fulfillment-agent-sa@ews-vs-icie-dev-npe-0bb5.iam.gserviceaccount.com \
  --member="user:amol.deshpande@equifax.com" \
  --role="roles/iam.serviceAccountUser"
```

This grants the `iam.serviceAccounts.actAs` permission required by Cloud Functions
to run the function as a dedicated service account.

## Why This is Needed

The Equifax org policy requires Cloud Functions to specify a service account.
The SA `fulfillment-agent-sa@ews-vs-icie-dev-npe-0bb5.iam.gserviceaccount.com`
already exists (created by me), but I cannot set IAM policy on it.

## Also Recommended (for the SA to work at runtime)

Grant these roles to the SA itself so the function can access Datastore and Pub/Sub:

```bash
for role in roles/datastore.user roles/pubsub.publisher roles/logging.logWriter; do
  gcloud projects add-iam-policy-binding ews-vs-icie-dev-npe-0bb5 \
    --member="serviceAccount:fulfillment-agent-sa@ews-vs-icie-dev-npe-0bb5.iam.gserviceaccount.com" \
    --role="$role" --condition=None --quiet
done
```

## Context

- Deploying to: Cloud Functions Gen 1 in **us-east1** (matching existing ICI functions)
- VPC Connector: `ews-vs-icie-dev-cn-2` (same as ICI-DatastoreExport-dev)
- Ingress: internal-only, Egress: all (matching org policy)
- The service: REST + MCP endpoints for fulfillment request processing
- All other infrastructure is ready: Pub/Sub topic/subscription created, code built

Thank you,
Amol Deshpande