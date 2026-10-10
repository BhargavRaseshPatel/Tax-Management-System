# AWS Architecture

Region **ca-central-1**. Infrastructure is CloudFormation in [`/infra`](../infra); deploy steps are in
[deployment.md](deployment.md). Built stage by stage; the diagram marks what exists as templates today.

```mermaid
flowchart LR
  user([Browser]) --> cf[CloudFront + S3<br/>frontend · Stage 15]
  user --> alb[Application Load Balancer<br/>Stage 15]
  subgraph VPC["VPC 10.20.0.0/16 · network.yaml ✔"]
    subgraph public["Public subnets (2 AZs)"]
      alb --> api[ECS Fargate: API<br/>security group civictax-app · Stage 14]
      worker[ECS Fargate: report worker<br/>Stage 13–14]
    end
    subgraph isolated["Isolated database subnets (2 AZs, no internet route)"]
      aurora[(Aurora PostgreSQL 17<br/>Serverless v2 · database.yaml ✔)]
    end
    api -- "5432 · TLS verify-full" --> aurora
  end
  api -. JWT .-> cognito[Amazon Cognito · Stage 8 swap-in]
  api --> sm[Secrets Manager<br/>DB credentials ✔]
  api --> s3[(S3 documents · storage.yaml ✔)]
  worker --> s3
  worker --> ddb[(DynamoDB report_jobs · Stage 12)]
  sfn[Step Functions · Stage 13] --> worker
```

## Network and database (Stage 10)
- **Isolation:** the database subnets' route table has only the in-VPC route. The cluster is `PubliclyAccessible: false`.
- **Who can reach the database:** only resources wearing the `civictax-app` security group, on port 5432.
  The database security group has no outbound rules.
- **Credentials:** `ManageMasterUserPassword` makes RDS generate the password and store it in Secrets Manager
  (KMS-encrypted). It never appears in a template, a parameter, the repo, or a terminal.
- **In transit:** `rds.force_ssl=1` rejects plain connections; the backend uses `sslmode=verify-full` with Amazon's
  RDS CA bundle (baked into the image), so it also refuses an impostor server.
- **At rest:** storage encryption with the AWS-managed key (customer-managed KMS key in Stage 16).
- **Schema:** identical to local. Flyway migrates on application start; local PostgreSQL 17 and Aurora PostgreSQL 17
  run the same SQL.

## Document storage (Stage 11)
- **Private by construction:** Block Public Access (all four), ACLs disabled, TLS-only bucket policy. There are no
  public objects and no presigned URLs: clients download through `GET /api/tax-bills/{id}/document`, so the same role
  and data-ownership rules as every other endpoint apply, and responses are `Cache-Control: no-store`.
- **Least privilege:** the application may only get and put objects under `tax-bills/` and `reports/`.
- **Encryption:** SSE-S3 by bucket default and on every put (customer-managed KMS key in Stage 16); versioning on.
- **Metadata** (key, size, generated time) lives in PostgreSQL on `tax_bills`; keys contain no personal data.
- **Local:** the `adobe/s3mock` emulator in Docker Compose and in tests. (LocalStack now requires a paid/registered
  token, so each AWS service gets its own small emulator instead.)

## Deliberate simplifications (demo cost) and their upgrade path
| Simplification | Production upgrade |
|---|---|
| No NAT gateway: Fargate tasks will run in public subnets with a public IP, protected by security groups | Private app subnets + NAT gateway or VPC endpoints (ECR, Secrets Manager, CloudWatch, S3) |
| One Aurora instance, 0–1 ACU, auto-pause | Second instance in another AZ (automatic failover), higher minimum capacity, no pause |
| Application connects as the master user (it needs DDL for Flyway) | Separate least-privilege runtime user; migrations run by a dedicated task/role |
| `DeletionProtection` off by default | On, plus longer backup retention |
| PDFs are generated inside the download request and proxied through the API | Generate in the async worker (Stage 13); presigned URLs or CloudFront signed URLs for large files |
