# Building Your Own Lambda Layer: Failure Flags Sidecar

This guide shows how to create your own Lambda layer for the Failure Flags Sidecar. This is useful when you need to customize the extension or when you're approaching AWS's layer limits and need to combine multiple extensions into a single layer.

---

## When to Build Your Own Layer

Consider building your own layer when:

- **Layer Limits**: Your project already uses several layers and you're approaching the 5-layer limit
- **Custom Configuration**: You need to bundle configuration files or scripts with the extension
- **Version Control**: You want to pin to a specific version of the sidecar
- **Corporate Requirements**: Your organization requires all dependencies to be built and stored internally
- **Combined Extensions**: You need to package multiple Lambda extensions together

Otherwise, we recommend using the [published layers](quickstart-lambda.md#1-use-gremlins-published-lambda-layer) for simplicity.

---

## Prerequisites

- AWS CLI configured with appropriate permissions
- `wget` or `curl` for downloading binaries
- `zip` utility for creating layer packages

---

## Step 1: Download the Gremlin Lambda Extension Binary

Choose the appropriate architecture for your Lambda functions:

### For x86_64 (Intel) Architecture:

```bash
# Create working directory
mkdir -p gremlin-lambda-layer/extensions

# Download x86_64 binary
wget https://assets.gremlin.com/packages/gremlin-lambda/latest/x86_64/gremlin-lambda-linux.tar.gz

# Extract binary
tar -xzf gremlin-lambda-linux.tar.gz

# Move to extensions directory and make executable
mv gremlin-lambda gremlin-lambda-layer/extensions/
chmod +x gremlin-lambda-layer/extensions/gremlin-lambda
```

### For arm64 (Graviton) Architecture:

```bash
# Create working directory
mkdir -p gremlin-lambda-layer/extensions

# Download arm64 binary
wget https://assets.gremlin.com/packages/gremlin-lambda/latest/arm64/gremlin-lambda-linux.tar.gz

# Extract binary
tar -xzf gremlin-lambda-linux.tar.gz

# Move to extensions directory and make executable
mv gremlin-lambda gremlin-lambda-layer/extensions/
chmod +x gremlin-lambda-layer/extensions/gremlin-lambda
```

## Step 2: Add Bootstrap Script (Required for Failure Flags by Proxy)

**IMPORTANT**: If you want to use **Failure Flags by Proxy** (intercepting Lambda runtime API calls), you must include a bootstrap script that sets the `AWS_LAMBDA_RUNTIME_API` environment variable.

### Use the Provided Bootstrap Script

Copy the bootstrap script from this repository:

```bash
# Copy the bootstrap script from the repo
cp scripts/bootstrap gremlin-lambda-layer/
chmod +x gremlin-lambda-layer/bootstrap
```

The bootstrap script automatically configures the Lambda runtime API redirection:

```bash
#!/bin/bash
port_to_use=${GREMLIN_LAMBDA_API_PROXY_PORT:-"localhost:5033"}
AWS_LAMBDA_RUNTIME_API="http://$port_to_use" exec -- "$@"
```

### Alternative: Create Custom Bootstrap Script

If you need to customize the bootstrap behavior, create your own:

```bash
cat > gremlin-lambda-layer/bootstrap << 'EOF'
#!/bin/bash
set -euo pipefail

# Configure Lambda Runtime API for Failure Flags by Proxy
port_to_use=${GREMLIN_LAMBDA_API_PROXY_PORT:-"localhost:5033"}
export AWS_LAMBDA_RUNTIME_API="http://$port_to_use"

# Set any additional custom environment variables
export GREMLIN_DEBUG=true

# Execute the original Lambda runtime with the modified environment
exec -- "$@"
EOF

chmod +x gremlin-lambda-layer/bootstrap
```

### What the Bootstrap Script Does

- **Redirects Lambda Runtime API**: Sets `AWS_LAMBDA_RUNTIME_API` to point to the sidecar proxy
- **Enables Failure Flags by Proxy**: Allows the sidecar to intercept Lambda invocations and responses
- **Configurable Port**: Uses `GREMLIN_LAMBDA_API_PROXY_PORT` if set, defaults to `localhost:5033`
- **Preserves Lambda Execution**: Passes through all original runtime arguments

## Step 3: Create the Layer Package

Package the extension into a ZIP file:

```bash
cd gremlin-lambda-layer
zip -r ../gremlin-lambda-layer.zip .
cd ..
```

Verify the structure:
```bash
unzip -l gremlin-lambda-layer.zip
```

Expected output (with bootstrap script for Failure Flags by Proxy):
```
Archive:  gremlin-lambda-layer.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
     xxxx  MM-DD-YYYY HH:MM   bootstrap
     xxxx  MM-DD-YYYY HH:MM   extensions/gremlin-lambda
---------                     -------
     xxxx                     2 files
```

**Note**: The `bootstrap` script is required at the root level if you want to use Failure Flags by Proxy functionality.

## Step 4: Publish the Lambda Layer

### Create the Layer:

```bash
aws lambda publish-layer-version \
  --layer-name gremlin-failure-flags-sidecar \
  --description "Gremlin Failure Flags Sidecar Extension" \
  --zip-file fileb://gremlin-lambda-layer.zip \
  --compatible-runtimes nodejs18.x nodejs20.x python3.9 python3.10 python3.11 python3.12 java11 java17 java21 dotnet6 dotnet8 go1.x provided provided.al2 provided.al2023 \
  --compatible-architectures x86_64
```

For arm64 architecture, replace `--compatible-architectures x86_64` with `--compatible-architectures arm64`.

### Note the Layer ARN

The command will return a layer ARN like:
```json
{
    "LayerArn": "arn:aws:lambda:us-east-1:123456789012:layer:gremlin-failure-flags-sidecar",
    "LayerVersionArn": "arn:aws:lambda:us-east-1:123456789012:layer:gremlin-failure-flags-sidecar:1",
    "Version": 1
}
```

Save the `LayerVersionArn` - you'll need it to attach to your Lambda functions.

## Step 5: Configure Your Lambda Function

### Add the Layer to Your Function:

```bash
aws lambda update-function-configuration \
  --function-name your-function-name \
  --layers arn:aws:lambda:us-east-1:123456789012:layer:gremlin-failure-flags-sidecar:1
```

### Environment Variables

Configure your Lambda function with the required environment variables:

```bash
aws lambda update-function-configuration \
  --function-name your-function-name \
  --environment Variables='{
    "GREMLIN_LAMBDA_ENABLED": "true",
    "GREMLIN_CONFIG_ARN": "arn:aws:secretsmanager:us-east-1:123456789012:secret:gremlin-config-abc123",
    "HTTP_PROXY": "http://localhost:5034",
    "HTTPS_PROXY": "http://localhost:5034"
  }'
```

### Environment Variable Explanation:

| Variable | Purpose | When Required |
|----------|---------|---------------|
| `GREMLIN_LAMBDA_ENABLED` | Enables the Lambda extension | **Always required** |
| `GREMLIN_CONFIG_ARN` | Location of your configuration | **Always required** |
| `HTTP_PROXY` | Routes outbound HTTP calls through dependency proxy | For dependency failure flags |
| `HTTPS_PROXY` | Routes outbound HTTPS calls through dependency proxy | For dependency failure flags |

**Important**: If you included the bootstrap script, you do **not** need to manually set `AWS_LAMBDA_RUNTIME_API`. The bootstrap script automatically configures this for Failure Flags by Proxy functionality.

### Optional: Custom Lambda Proxy Port

If you need to use a different port for the Lambda proxy, you can set:

```bash
aws lambda update-function-configuration \
  --function-name your-function-name \
  --environment Variables='{
    "GREMLIN_LAMBDA_ENABLED": "true",
    "GREMLIN_CONFIG_ARN": "arn:aws:secretsmanager:us-east-1:123456789012:secret:gremlin-config-abc123",
    "GREMLIN_LAMBDA_API_PROXY_PORT": "localhost:5035",
    "HTTP_PROXY": "http://localhost:5034",
    "HTTPS_PROXY": "http://localhost:5034"
  }'
```

The bootstrap script will automatically use the custom port for `AWS_LAMBDA_RUNTIME_API`.

## Step 6: Configure Your Sidecar Configuration File

Create your sidecar configuration with Lambda proxy enabled:

### Configuration File:
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

lambda_proxy_enabled: true
lambda_proxy_port: localhost:5033  # Default port

dependency_proxy_enabled: true
dependency_proxy_port: localhost:5034
```

### Optional: Custom Lambda Proxy Port

If you need to use a different port for the Lambda proxy:

```yaml
lambda_proxy_enabled: true
lambda_proxy_port: localhost:5035  # Custom port
```

The bootstrap script will automatically detect the custom port via the `GREMLIN_LAMBDA_API_PROXY_PORT` environment variable.

## Step 7: Create Configuration

Create your sidecar configuration in AWS Secrets Manager or Parameter Store:

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

lambda_proxy_enabled: true
lambda_proxy_port: localhost:5033

dependency_proxy_enabled: true
dependency_proxy_port: localhost:5034
```

Store in AWS Secrets Manager:
```bash
aws secretsmanager create-secret \
  --name gremlin-lambda-config \
  --description "Gremlin Failure Flags Lambda Configuration" \
  --secret-string file://lambda-config.yaml
```

## Step 8: Update Lambda Permissions

Add permissions for your Lambda function to read the configuration:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:us-east-1:123456789012:secret:gremlin-lambda-config-*"
    }
  ]
}
```

## Step 9: Test Your Custom Layer

Deploy and test your Lambda function:

1. **Invoke your function** and check CloudWatch Logs for extension startup messages (to see Debug logs, set `debug: true` in the configuration file:
   ```
   [EXTENSION] gremlin-lambda extension starting
   [DEBUG] Lambda proxy enabled on localhost:5033
   [DEBUG] Dependency proxy enabled on localhost:5034
   ```

2. **Verify Failure Flags** appear in the [Gremlin UI](https://app.gremlin.com/failure-flags/list)

3. **Test experiment execution** by triggering a failure on the Lambda flags

## Troubleshooting Custom Layers

### Extension Not Starting:
- Check CloudWatch Logs for error messages
- Verify binary permissions: `chmod +x extensions/gremlin-lambda`
- Ensure correct architecture (x86_64 vs arm64) matches your Lambda function

### Runtime API Errors:
- Verify `AWS_LAMBDA_RUNTIME_API=localhost:5033` is set
- Check that `lambda_proxy_port` matches the runtime API setting
- Enable debug logging: `GREMLIN_DEBUG=true` in environment variables or `debug: true` in the configuration file: 

### Layer Too Large:
Lambda layers have a 50MB limit (unzipped). If your layer exceeds this:
- Remove unnecessary files from the package
- Consider using multiple smaller layers
- Use external storage (S3) for large configuration files

## Advanced: Combining Multiple Extensions

If you need to package multiple Lambda extensions in one layer:

```bash
# Create combined layer structure
mkdir -p combined-layer/extensions

# Add Gremlin extension
cp gremlin-lambda combined-layer/extensions/

# Add other extensions
cp other-extension combined-layer/extensions/

# Package combined layer
cd combined-layer
zip -r ../combined-extensions-layer.zip .
```

**Note**: Ensure extensions don't conflict on ports or resources.

---

## Summary

Building your own Lambda layer gives you control over versioning and packaging, but requires careful attention to:

1. **Correct binary architecture** (x86_64 vs arm64)
2. **Critical environment variable**: `AWS_LAMBDA_RUNTIME_API=localhost:5033`
3. **Proper permissions** for configuration access
4. **Port configuration** matching between proxy and runtime API

For most use cases, the [published layers](quickstart-lambda.md) are simpler and automatically maintained with updates.
