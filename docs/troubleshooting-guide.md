# Troubleshooting Guide: Failure Flags Sidecar

This guide helps you diagnose and resolve common issues with the Failure Flags Sidecar. Follow the systematic approach below to identify and fix problems quickly.

---

## Step 1: Always Enable Debug Logging First

**Before troubleshooting any issue**, always enable debug logging to get detailed information about what the sidecar is doing.

### Enable Debug Logging

**Option 1: Environment Variable** (Recommended)
```bash
GREMLIN_DEBUG=true
```

**Option 2: Configuration File**
```yaml
debug: true
```

### What Debug Logging Shows
- Sidecar startup sequence
- Configuration loading and validation
- Proxy initialization status
- Service registration with Gremlin
- Incoming request processing
- Experiment evaluation and execution

### Viewing Debug Logs

**AWS Lambda**: Check CloudWatch Logs for your function
```bash
aws logs tail /aws/lambda/your-function-name --follow
```

**AWS ECS**: Check ECS service logs
```bash
aws logs tail /ecs/failure-flags-sidecar --follow
```

**Kubernetes**: Check pod logs
```bash
kubectl logs -f deployment/your-app -c failure-flags-sidecar
```

**Docker**: Check container logs
```bash
docker logs -f your-container-name
```

---

## Step 2: For Gremlin Connection Issues, Enable Trace Logging

If you suspect issues communicating with Gremlin's control plane, enable trace logging for detailed network information.

### Enable Trace Logging

**Option 1: Environment Variable**
```bash
GREMLIN_TRACE=true
GREMLIN_DEBUG=true  # Keep debug enabled too
```

**Option 2: Configuration File**
```yaml
debug: true
trace: true
```

### What Trace Logging Shows
- HTTP requests to Gremlin API
- TLS handshake details
- Network timeouts and retries
- Response codes and error messages
- Certificate validation issues
- Corporate proxy interactions

---

## Common Issues and Solutions

### 1. Sidecar Not Starting

**Symptoms:**
- Container exits immediately
- No log output from sidecar
- Health checks failing

**Debugging Steps:**
1. **Enable debug logging** (see Step 1)
2. Check the logs for configuration errors
3. Verify required environment variables are set

**Common Causes & Solutions:**

**Missing enablement variable:**
```bash
# Lambda
GREMLIN_LAMBDA_ENABLED=true

# ECS/Kubernetes/Other
GREMLIN_SIDECAR_ENABLED=true
```

**Invalid configuration file path:**
```bash
# Check file exists and is accessible
GREMLIN_CONFIG_FILE=/path/to/config.yaml
```

**AWS ARN access issues:**
```bash
# Verify ARN format and permissions
GREMLIN_CONFIG_ARN=arn:aws:secretsmanager:us-east-1:123:secret:config-abc123
```

### 2. Cannot Connect to Gremlin Control Plane

**Symptoms:**
- "Failed to register service" errors
- "Connection timeout" messages
- No Failure Flags appearing in Gremlin UI

**Debugging Steps:**
1. **Enable trace logging** (see Step 2)
2. Check network connectivity to `api.gremlin.com`
3. Verify credentials and team ID

**Common Causes & Solutions:**

**Invalid credentials:**
- Verify `team_id`, `team_certificate`, and `team_private_key`
- Check credentials at https://app.gremlin.com/settings/teams
- Ensure newlines in certificates are preserved (`\n` or multi-line YAML)

**Network/Firewall issues:**
```bash
# Test connectivity (if available in container)
curl -v https://api.gremlin.com/v1/ff/health
```

**Corporate proxy required:**
```yaml
https_proxy: https://corp.proxy.internal:3128
```

**Custom CA certificate needed:**
```yaml
ssl_cert: |
  -----BEGIN CERTIFICATE-----
  ...
  -----END CERTIFICATE-----
```

### 3. No Failure Flags Appearing in Gremlin UI

**Symptoms:**
- Sidecar starts successfully
- No flags visible in Gremlin web interface
- No experiments available

**Debugging Steps:**
1. **Enable debug logging**
2. Drive traffic through your application
3. Check proxy configuration

**Common Causes & Solutions:**

**No traffic flowing through proxies:**
- Verify `HTTP_PROXY` and `HTTPS_PROXY` are set in your application
- Check ingress proxy routing (load balancer configuration)
- Ensure dependency proxy is receiving requests

**Service name issues:**
- For Kubernetes: Explicitly set `SERVICE_NAME` (not auto-detected)
- Check service name in debug logs matches expectations

**Proxy not enabled:**
```yaml
dependency_proxy_enabled: true  # For dependency flags
ingress_proxy_enabled: true     # For ingress flags
lambda_proxy_enabled: true      # For Lambda flags
```

### 4. Application Cannot Reach Dependencies

**Symptoms:**
- HTTP requests from application fail
- "Connection refused" errors
- Timeouts on outbound calls

**Debugging Steps:**
1. **Enable debug logging**
2. Check if dependency proxy is running
3. Verify proxy environment variables

**Solutions:**

**Set proxy environment variables in application:**
```bash
HTTP_PROXY=http://localhost:5034
HTTPS_PROXY=http://localhost:5034
```

**Check dependency proxy port:**
```yaml
dependency_proxy_port: localhost:5034  # Default
```

**Verify proxy is accessible:**
```bash
# Test proxy (if curl available)
curl -x http://localhost:5034 https://httpbin.org/get
```

### 5. Load Balancer Health Checks Failing

**Symptoms:**
- Service marked unhealthy by load balancer
- 502/503 errors from load balancer
- Traffic not reaching application

**Debugging Steps:**
1. **Enable debug logging**
2. Check ingress proxy configuration
3. Verify load balancer target configuration

**Solutions:**

**Update load balancer to target sidecar port:**
```bash
# Target port 5035 instead of application port
aws elbv2 modify-target-group --target-group-arn <arn> --port 5035
```

**Verify ingress proxy configuration:**
```yaml
ingress_proxy_enabled: true
ingress_proxy_port: :5035
ingress_proxied_endpoint: http://localhost:8080  # Your app port
```

**Update health check path if needed:**
- Point health checks to a path that works through the proxy

### 6. Lambda-Specific Issues

**Symptoms:**
- Lambda function timeouts
- Extension not starting
- Runtime API errors

**Debugging Steps:**
1. **Enable debug logging**
2. Check CloudWatch Logs for extension lifecycle
3. Verify layer attachment

**Solutions:**

**Use correct environment variable:**
```bash
GREMLIN_LAMBDA_ENABLED=true  # Not GREMLIN_SIDECAR_ENABLED
```

**Verify layer architecture matches function:**
- Use x86_64 layer for x86_64 functions
- Use arm64 layer for arm64 functions

**Check lambda proxy configuration:**
```yaml
lambda_proxy_enabled: true
lambda_proxy_port: :5032  # Default
```

### 7. ECS-Specific Issues

**Symptoms:**
- Task fails to start
- Sidecar container exits
- Service registration issues

**Debugging Steps:**
1. **Enable debug logging**
2. Check ECS service events
3. Verify IAM permissions

**Solutions:**

**Verify task role has required permissions:**
```json
{
  "Effect": "Allow",
  "Action": [
    "secretsmanager:GetSecretValue",
    "ssm:GetParameter"
  ],
  "Resource": "arn:aws:secretsmanager:*:*:secret:gremlin-config-*"
}
```

**Check container dependencies:**
```json
{
  "dependsOn": [
    {
      "containerName": "failure-flags-sidecar",
      "condition": "HEALTHY"
    }
  ]
}
```

### 8. Kubernetes-Specific Issues

**Symptoms:**
- Pod fails to start
- Service name not detected
- Network policies blocking traffic

**Debugging Steps:**
1. **Enable debug logging**
2. Check pod events and logs
3. Verify service account permissions

**Solutions:**

**Explicitly set service name (required):**
```bash
SERVICE_NAME=my-service  # Not auto-detected in Kubernetes
```

**Check network policies:**
- Ensure pods can reach external API (api.gremlin.com)
- Verify internal pod-to-pod communication

**Verify resource limits:**
```yaml
resources:
  requests:
    memory: "64Mi"
    cpu: "50m"
  limits:
    memory: "128Mi"
    cpu: "100m"
```

---

## Health Check Command

The sidecar includes a health check command to verify it's running properly:

```bash
# Run health check (exit code 0 = healthy)
failure-flags-sidecar -s
```

**Use in container health checks:**
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD failure-flags-sidecar -s
```

---

## Getting Help

If you've followed this troubleshooting guide and still have issues:

1. **Always include debug logs** in support requests
2. **Include trace logs** for connection issues
3. Specify your platform (Lambda, ECS, Kubernetes, etc.)
4. Include relevant configuration (with credentials redacted)

**Log Examples to Include:**
```bash
# Good - includes startup and error context
[DEBUG] Loading configuration from /config/ff-config.yaml
[DEBUG] Dependency proxy enabled on localhost:5034
[ERROR] Failed to connect to api.gremlin.com: connection timeout

# Not helpful - missing context
[ERROR] Connection failed
```

---

## Quick Debugging Checklist

When facing issues, run through this checklist:

- [ ] **Debug logging enabled** (`GREMLIN_DEBUG=true`)
- [ ] **Sidecar enabled** (`GREMLIN_SIDECAR_ENABLED=true` or `GREMLIN_LAMBDA_ENABLED=true`)
- [ ] **Valid credentials** (team ID, certificate, private key)
- [ ] **Configuration file accessible** (path/ARN correct, permissions granted)
- [ ] **Network connectivity** to `api.gremlin.com`
- [ ] **Proxy environment variables set** in application (`HTTP_PROXY`, `HTTPS_PROXY`)
- [ ] **Service name set explicitly** (Kubernetes only)
- [ ] **Platform-specific setup complete** (load balancer, layer, IAM permissions)

For connection issues, also enable:
- [ ] **Trace logging enabled** (`GREMLIN_TRACE=true`)