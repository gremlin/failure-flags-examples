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
# this value depends on Option A versus Option B
# in step #5 below.
ingress_proxy_port: :80
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
    "command": ["CMD", "/failure-flags-sidecar", "-s"],
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
The `healthCheck` configuration uses the sidecar's built-in health check command (`failure-flags-sidecar -s`) to verify the sidecar is running properly. This helps ECS detect when the sidecar is ready and ensures your application container waits for the sidecar to be healthy before starting. Port `5032` is the sidecar's local control port to which the health check connects.

This **must** use the exec form (`"CMD"`) with the binary's absolute path, not `"CMD-SHELL"`. The sidecar image has no shell and sets no `PATH`, so a `CMD-SHELL` command will fail on every single check. Similarly, the full path (`/failure-flags-sidecar`) must be used.

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

**A note on the `dependsOn` condition:** `condition: HEALTHY` means the app won't start until the sidecar's `healthCheck` passes at least once. That check starts alongside the sidecar's proxy listeners (egress on `dependency_proxy_port`, ingress on `ingress_proxy_port`), so in practice it closes the small startup race where the app's first outbound request through `HTTP_PROXY` could otherwise hit connection-refused before the egress proxy has bound its port. The cost is a small, bounded delay to the app's startup (roughly one health check interval) on every task launch.

Alternatively, if you'd rather not pay that delay and can tolerate the (usually sub-second) race window, use `condition: START` to let the app start as soon as the sidecar container process begins, without waiting for any check to pass. This does create a small and unlikely window where egress requests made by the app are susceptible to connection errors while the failure-flags-sidecar process launches.

**NOTE**: a passing container healthCheck is not confirmation that the sidecar is registered with Gremlin. It only verifies that the sidecar's local control port is listening. A misconfigured sidecar will still report healthy, join your service normally, and serve traffic, but it won't provide failure injection. Confirm the sidecar is actually working via the Gremlin UI or by checking your logs for `Registered with Gremlin Data Plane API`, not via ECS's health status.

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

## 5. Route Traffic Through the Ingress Proxy

Next you need to tell your load balancer or API gateway to route requests through the ingress proxy instead of directly to your application. There are two ways to do this.

### Option A (recommended): Move the app's port, let the sidecar take over

Change the port your application listens on (e.g. from 80 to 9080), set `ingress_proxy_port` in `ff-config.yaml` to the app’s original port (e.g. `:80`), and set `ingress_proxied_endpoint` to the app’s new port (e.g. `http://localhost:9080`).

**Your load balancer / target group configuration never changes**. Before the rollout it was pointing at your app on that port. After the rollout, it's pointing at the sidecar on that same port. Only the ECS service's container registration changes, which is a routine rolling service update, not an infrastructure change. This also makes rollback safe and symmetric: reverting the task definition to drop the sidecar puts the app straight back on its original port, again with no load balancer changes required.

### Option B: Point the load balancer at a new sidecar port

Change your service container port to something other than 80 (like 9080), then update your load balancer target group to send traffic to the sidecar on port 5035 instead of directly to your application.

```bash
aws elbv2 modify-target-group \
  --target-group-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/your-app/1234567890123456 \
  --port 5035
```

This requires an actual load balancer/target group change to roll out, and another to roll back.

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
- **No failure flags appearing, even though the task and sidecar both report healthy**: A passing healthCheck only confirms the sidecar's local control port is listening, not that it successfully loaded its configuration or registered with Gremlin. The sidecar fails safe by staying up in a no-op state rather than exiting, so a misconfigured sidecar looks identical to a working one from ECS's perspective. Verify `GREMLIN_CONFIG_ARN` points to the correct resource, and check your logs for `Registered with Gremlin Data Plane API` (or an error loading configuration) rather than relying on ECS health status.
- **Application can't reach dependencies**: Ensure `HTTP_PROXY` and `HTTPS_PROXY` environment variables are set correctly
- **Load balancer health checks failing**: Update health check path to target the sidecar port (5035, or the port it took over under Option A in step 5) instead of the application port
- **Sidecar task cycles forever / never reports healthy, even though it's clearly running**: Check that `healthCheck.command` uses the exec form (`"CMD"`) with the binary's absolute path (`["CMD", "/failure-flags-sidecar", "-s"]`), not `"CMD-SHELL"`. The sidecar image has no shell, so a `CMD-SHELL` command fails to even start, on every check, regardless of the sidecar's actual state — this can look identical to a real problem (functioning sidecar, serving traffic, still marked unhealthy) since nothing about the sidecar's own behavior is actually wrong.
- **Task replaced before it's had a chance to come up**: size the ECS service's `healthCheckGracePeriodSeconds` to cover the sidecar's own startup latency (image pull, fetching/decrypting its config, connecting to Gremlin's control plane) on top of your application's normal startup time. The default grace period is `0`, which gives ECS no room to wait before acting on a failing health check.
