# Quickstart: Failure Flags Sidecar by Proxy on AWS ECS

This guide shows how to add the Failure Flags Sidecar to your AWS ECS service with **no code changes**. It enables both the **dependency proxy** (egress) and **ingress proxy**, so you can begin testing failure modes immediately.

---

## Before Enabling and Connecting the FF Sidecar Proxy

![Request routing without Failure Flags](./images/Without%20FF.png)

## After Enabling and Connecting the FF Sidecar Proxy

![Request routing with Failure Flags by Proxy](./images/With%20FFbP.png)

---

## 1. Create the Sidecar Config File

Create `ff-config.yaml` with your Gremlin credentials:

```yaml
team_id: <your-gremlin-team-id>

team_certificate: |
  -----BEGIN CERTIFICATE-----
  ...
  -----END CERTIFICATE-----

team_private_key: |
  -----BEGIN EC PRIVATE KEY-----
  ...
  -----END EC PRIVATE KEY-----

dependency_proxy_enabled: true
dependency_proxy_port: localhost:5034

ingress_proxy_enabled: true
ingress_proxy_port: :5035
ingress_proxied_endpoint: http://localhost:9080
```

Replace the team_id, team_certificate, and team_private_key with your actual Gremlin credentials from https://app.gremlin.com/settings/teams.

## 2. Store Configuration in AWS Parameter Store or Secrets Manager

### Option A: AWS Systems Manager Parameter Store

```bash
aws ssm put-parameter \
  --name "/gremlin/failure-flags-config" \
  --type "SecureString" \
  --value file://ff-config.yaml \
  --description "Gremlin Failure Flags Sidecar Configuration"
```

### Option B: AWS Secrets Manager

```bash
aws secretsmanager create-secret \
  --name gremlin-config \
  --description "Gremlin Failure Flags Sidecar Configuration" \
  --secret-string file://ff-config.yaml
```

Note the ARN returned by either command - you'll need it for the task definition.

## 3. Update ECS Task Definition

### Add the Sidecar Container

Add a new container to your ECS task definition:

```json
{
  "name": "failure-flags-sidecar",
  "image": "docker.io/gremlin/failure-flags-sidecar:v2",
  "essential": true,
  "portMappings": [
    { "containerPort": 5032 },
    { "containerPort": 5034 }, 
    { "containerPort": 5035 }
  ],
  "environment": [
    { "name": "GREMLIN_SIDECAR_ENABLED", "value": "true" },
    { "name": "GREMLIN_CONFIG_ARN", "value": "arn:aws:ssm:us-east-1:123456789012:parameter/gremlin/failure-flags-config" }
  ],
  "healthCheck": {
    "command": ["CMD-SHELL", "failure-flags-sidecar -s || exit 1"],
    "interval": 30,
    "timeout": 5,
    "retries": 3,
    "startPeriod": 10
  },
  "logConfiguration": {
    "logDriver": "awslogs",
    "options": {
      "awslogs-group": "/ecs/failure-flags-sidecar",
      "awslogs-region": "us-east-1",
      "awslogs-stream-prefix": "ecs"
    }
  }
}
```

Replace the `GREMLIN_CONFIG_ARN` value with the actual ARN from step 2.

**Health Check Configuration:**
The `healthCheck` configuration uses the sidecar's built-in health check command (`failure-flags-sidecar -s`) to verify the sidecar is running properly. This helps ECS detect when the sidecar is ready and ensures your application container waits for the sidecar to be healthy before starting.

### Update Your Application Container

Add proxy environment variables to your application container:

```json
{
  "name": "your-app",
  "image": "your-app:latest",
  "environment": [
    { "name": "HTTP_PROXY", "value": "http://localhost:5034" },
    { "name": "HTTPS_PROXY", "value": "http://localhost:5034" }
  ],
  "dependsOn": [
    {
      "containerName": "failure-flags-sidecar",
      "condition": "HEALTHY"
    }
  ]
}
```

## 4. Update ECS Task Role Permissions

Add permissions for the ECS task to read from AWS Parameter Store or Secrets Manager:

### For Parameter Store:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": "arn:aws:ssm:us-east-1:123456789012:parameter/gremlin/failure-flags-config"
    }
  ]
}
```

### For Secrets Manager:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:us-east-1:123456789012:secret:gremlin-config-*"
    }
  ]
}
```

## 5. Update Load Balancer Configuration

Next you need to tell your load balancer or API gateway to route requests to your application through the ingress proxy.

Change your service container port to something other than 80 (like 9080), then update your load balancer target group to send traffic to the sidecar on port 5035 instead of directly to your application.

Alternatively, change the port your application runs on and set the ingress proxy to run on that port instead, then set the ingress_proxied_endpoint to the new port your application is running on.

### Application Load Balancer Target Group:

```bash
aws elbv2 modify-target-group \
  --target-group-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/your-app/1234567890123456 \
  --port 5035
```

## 6. Deploy Your Updated ECS Service

```bash
aws ecs update-service \
  --cluster your-cluster-name \
  --service your-service-name \
  --task-definition your-task-definition:new-revision
```

## 7. Result: Automatically Created Failure Flags

Once running, the sidecar will create the following Failure Flags with no code changes:

### For Each Incoming Request:

* ingress – Flag on all incoming requests
* http-ingress – Flag on HTTP-specific inbound behavior
* response – Flag when sending responses

### For Each Outgoing Dependency:

* dependency-<hostname> – One flag per remote host your service contacts

These flags can be triggered from the Gremlin web UI or API to simulate latency, errors, and dropped connections.

## 8. Testing Your Setup

1. Deploy your ECS service with the updated task definition
2. Check the ECS service logs for sidecar startup messages
3. Drive traffic to your service through the load balancer
4. Visit the [Gremlin UI](https://app.gremlin.com/failure-flags/list) to see your service's Failure Flags
5. Create your first experiment by triggering a failure on the `ingress` Failure Flag

## 9. Troubleshooting

- **Always**: As a first step to troubleshooting always enable debug (via environment variable or configuration file)
- **Sidecar not starting**: The logs should be explicit in configuration issues. Check ECS task role has correct permissions for Parameter Store/Secrets Manager.
- **Sidecar cannot reach Gremlin**: Enable trace logging (via environment variable or configuration file) and determine if the issue is related to routability (or filewall) issues, credential validity, etc.
- **No failure flags appearing**: Verify `GREMLIN_CONFIG_ARN` points to the correct resource and traffic is flowing through the service  
- **Application can't reach dependencies**: Ensure `HTTP_PROXY` and `HTTPS_PROXY` environment variables are set correctly
- **Load balancer health checks failing**: Update health check path to target the sidecar port (5035) instead of the application port
