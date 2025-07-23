# Configuration Guide: Failure Flags Sidecar

This guide provides detailed information about configuring the Failure Flags Sidecar, including all available configuration properties, their corresponding environment variables, and platform-specific considerations.

---

## Configuration Methods

The sidecar accepts configuration through two methods:

1. **Configuration Files** (YAML format) 
2. **Environment Variables** (override config file values)

Environment variables always take precedence over configuration file values.

---

## Required Configuration

### Enabling the Sidecar

**Always Required**: One of these environment variables must be set to enable the sidecar:

| Environment Variable | Description | Accepted Values |
|---------------------|-------------|-----------------|
| `GREMLIN_SIDECAR_ENABLED` | Enable sidecar for long-running processes | `true`, `yes`, `1` |
| `GREMLIN_LAMBDA_ENABLED` | Enable sidecar for AWS Lambda | `true`, `yes`, `1` |

If neither is set or set to any other value, the sidecar operates in NOOP mode.

### Configuration File Location

**Required if using config file**: Specify the location of your configuration file:

| Environment Variable | Description | Example |
|---------------------|-------------|---------|
| `GREMLIN_CONFIG_FILE` | Fully-qualified path to config file | `/config/ff-config.yaml` |
| `GREMLIN_CONFIG_ARN` | ARN of resource containing config file | `arn:aws:secretsmanager:us-east-1:123:secret:gremlin-config` |
| `GREMLIN_CONFIG_ARN_ROLE` | IAM role to assume for ARN access | `arn:aws:iam::123:role/GremlinConfigRole` |

### Gremlin Credentials

**Always Required**: These credentials authenticate with Gremlin's control plane:

| Config Property | Environment Variable | Description |
|----------------|---------------------|-------------|
| `team_id` | `GREMLIN_TEAM_ID` | Your Gremlin Team ID |
| `team_certificate` | `GREMLIN_TEAM_CERTIFICATE` | Team certificate content |
| `team_private_key` | `GREMLIN_TEAM_PRIVATE_KEY` | Team private key content |

**Alternative credential sources**:

| Config Property | Environment Variable | Description |
|----------------|---------------------|-------------|
| `team_certificate_file` | `GREMLIN_TEAM_CERTIFICATE_FILE` | Path to certificate file |
| `team_certificate_arn` | `GREMLIN_TEAM_CERTIFICATE_ARN` | ARN containing certificate |
| `team_private_key_file` | `GREMLIN_TEAM_PRIVATE_KEY_FILE` | Path to private key file |
| `team_private_key_arn` | `GREMLIN_TEAM_PRIVATE_KEY_ARN` | ARN containing private key |

Get your credentials from https://app.gremlin.com/settings/teams.

---

## Service Identity Configuration

### Service Name

The service name identifies your service in the Gremlin UI and is used for organizing Failure Flags.

| Config Property | Environment Variable | Description |
|----------------|---------------------|-------------|
| `service_name` | `SERVICE_NAME` | Explicit service name override |

**Platform-specific behavior**:

- **AWS Lambda**: Automatically detected from function name and environment
- **AWS ECS**: Automatically detected from task definition and service metadata
- **Kubernetes**: **NOT automatically detected** - must be set explicitly
- **Other environments**: Uses hostname if not specified

**Best practice**: Let the sidecar auto-detect the service name unless you need explicit control.

### Service Labels

Add metadata labels to identify unique deployments:

| Config Property | Environment Variable | Description |
|----------------|---------------------|-------------|
| `labels` | `GREMLIN_LABEL_*` | Key-value pairs for service metadata |

**Example**:
```yaml
labels:
  datacenter: corp-na1
  project: columbia
  version: "1.2.3"
```

**Environment variable equivalent**:
```bash
GREMLIN_LABEL_DATACENTER=corp-na1
GREMLIN_LABEL_PROJECT=columbia
GREMLIN_LABEL_VERSION=1.2.3
```

---

## Failure Flags by Proxy Configuration (No-Code / Low-Code)

### AWS Lambda API Proxy

**Most relevant for**: AWS Lambda environments

| Config Property | Environment Variable | Description | Default |
|----------------|---------------------|-------------|---------|
| `lambda_proxy_enabled` | `GREMLIN_LAMBDA_PROXY_ENABLED` | Enable Lambda Runtime API proxy | `false` |
| `lambda_proxy_port` | `GREMLIN_LAMBDA_PROXY_PORT` | Lambda proxy bind address | `localhost:5033` |

### Dependency Proxy (Egress)

**Most relevant for**: All environments where services make outbound HTTP calls

| Config Property | Environment Variable | Description | Default |
|----------------|---------------------|-------------|---------|
| `dependency_proxy_enabled` | `GREMLIN_DEPENDENCY_PROXY_ENABLED` | Enable HTTP CONNECT proxy for dependencies | `false` |
| `dependency_proxy_port` | `GREMLIN_DEPENDENCY_PROXY_PORT` | Dependency proxy bind address | `localhost:5034` |

**Usage**: Set `HTTP_PROXY` and `HTTPS_PROXY` environment variables in your application to point to this proxy.

### Ingress Proxy (Inbound)

**Most relevant for**: ECS and Kubernetes environments with load balancers

| Config Property | Environment Variable | Description | Default |
|----------------|---------------------|-------------|---------|
| `ingress_proxy_enabled` | `GREMLIN_INGRESS_PROXY_ENABLED` | Enable reverse proxy for inbound requests | `false` |
| `ingress_proxy_port` | `GREMLIN_INGRESS_PROXY_PORT` | Ingress proxy bind address | `:5035` |
| `ingress_proxied_endpoint` | `GREMLIN_INGRESS_PROXIED_ENDPOINT` | URL of your application | **Required if ingress enabled** |

### Common Proxy Settings

| Config Property | Environment Variable | Description | Default |
|----------------|---------------------|-------------|---------|
| `proxy_idle_connection_timeout` | `GREMLIN_PROXY_IDLE_TIMEOUT` | Idle connection timeout | `2m` |
| `proxy_read_timeout` | `GREMLIN_PROXY_READ_TIMEOUT` | Read timeout | `2m` |
| `proxy_write_timeout` | `GREMLIN_PROXY_WRITE_TIMEOUT` | Write timeout | `2m` |

---

## Platform-Specific Recommendations

### AWS Lambda

**Required proxy configuration**:
```yaml
lambda_proxy_enabled: true
dependency_proxy_enabled: true  # If making outbound calls
```

**Key environment variables**:
```bash
GREMLIN_LAMBDA_ENABLED=true
GREMLIN_CONFIG_ARN=arn:aws:secretsmanager:region:account:secret:name
```

**Additional setup**:

Your Lambda function must have an additional environment variable set, `AWS_LAMBDA_RUNTIME_API`, which must correspond to the port where the Lambda proxy is running. This is handled automatically when you use the provided Lambda Layer.

### AWS ECS

**Required proxy configuration**:
```yaml
dependency_proxy_enabled: true  # For outbound calls
ingress_proxy_enabled: true     # For inbound traffic
ingress_proxied_endpoint: http://localhost:9080
```

**Key environment variables**:
```bash
GREMLIN_SIDECAR_ENABLED=true
GREMLIN_CONFIG_ARN=arn:aws:ssm:region:account:parameter/path
```

**Additional setup**: Update load balancer, API gateway or other to route to sidecar port (5035).

### Kubernetes

**Required configuration**:
```yaml
service_name: my-service  # Must be set explicitly
dependency_proxy_enabled: true
ingress_proxy_enabled: true     # If using ingress
ingress_proxied_endpoint: http://localhost:8080
```

**Key environment variables**:
```bash
GREMLIN_SIDECAR_ENABLED=true
SERVICE_NAME=my-service  # Required - not auto-detected
```

---

## Network and Security Configuration

### Corporate Proxy Support

| Config Property | Environment Variable | Description |
|----------------|---------------------|-------------|
| `https_proxy` | `HTTPS_PROXY` | Corporate HTTPS proxy for Gremlin API calls |

### Custom Certificate Authority

| Config Property | Environment Variable | Description |
|----------------|---------------------|-------------|
| `ssl_cert` | `GREMLIN_CUSTOM_ROOT_CERTIFICATE` | Custom CA certificate content |
| `ca_cert_file` | `GREMLIN_CUSTOM_ROOT_CERTIFICATE_FILE` | Path to CA certificate file |
| `ca_cert_arn` | `GREMLIN_CUSTOM_ROOT_CERTIFICATE_ARN` | ARN containing CA certificate |
| `ssl_trust_cert_bundle_file` | `GREMLIN_CUSTOM_ROOT_CERTIFICATE_BUNDLE_FILE` | Path to certificate bundle |
| `ssl_trust_cert_bundle_arn` | `GREMLIN_CUSTOM_ROOT_CERTIFICATE_BUNDLE_ARN` | ARN containing certificate bundle |

---

## Advanced Configuration

### Timing and Intervals

| Config Property | Environment Variable | Description | Default |
|----------------|---------------------|-------------|---------|
| `refresh_interval` | `GREMLIN_REFRESH_INTERVAL` | How often to check for experiments | `30s` |
| `request_timeout` | `GREMLIN_REQUEST_TIMEOUT` | Timeout for API requests | `500ms` |

### API Endpoint

| Config Property | Environment Variable | Description | Default |
|----------------|---------------------|-------------|---------|
| `api_endpoint_url` | `GREMLIN_API_ENDPOINT_URL` | Gremlin API endpoint | `https://api.gremlin.com/v1/ff` |

**Use cases**: Private Edition or custom endpoints (e.g., Private Link).

### Debugging

| Config Property | Environment Variable | Description | Default |
|----------------|---------------------|-------------|---------|
| `debug` | `GREMLIN_DEBUG` | Enable debug logging | `false` |
| `trace` | `GREMLIN_TRACE` | Enable network tracing | `false` |

**Recommendation**: Always enable debug logging during initial setup.

---

## Ports Reference

| Service | Port | Description |
|---------|------|-------------|
| Failure Flags Lookup | 5032 | SDK communication |
| Lambda Runtime API Proxy | 5033 | Lambda runtime interception |
| Dependency Proxy | 5034 | Outbound HTTP/HTTPS proxy |
| Ingress Proxy | 5035 | Inbound request proxy |

---

## Environment-Only Quickstart

For minimal setup using only environment variables (no config file):

```bash
# Required - Enable sidecar
GREMLIN_SIDECAR_ENABLED=true

# Required - Credentials
GREMLIN_TEAM_ID=your-team-id
GREMLIN_TEAM_CERTIFICATE="-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----"
GREMLIN_TEAM_PRIVATE_KEY="-----BEGIN EC PRIVATE KEY-----\n...\n-----END EC PRIVATE KEY-----"

# Platform-specific
SERVICE_NAME=my-service  # Required for Kubernetes

# Enable proxies as needed
GREMLIN_DEPENDENCY_PROXY_ENABLED=true
GREMLIN_INGRESS_PROXY_ENABLED=true
GREMLIN_INGRESS_PROXIED_ENDPOINT=http://localhost:8080
```
