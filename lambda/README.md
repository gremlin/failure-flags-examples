# Failure Flags Lambda Examples

A set of AWS Lambda functions deployed behind an API Gateway, designed for demonstrating failure flag injection with Gremlin.

## Architecture

- **Router** -- an HTTP API Gateway forwards all requests to this function. It serves an HTML index at `/` and proxies `/a`, `/b`, and `/c` to the three backend services by invoking them via the AWS Lambda SDK.
- **ServiceA, ServiceB, ServiceC** -- backend functions that return a JSON payload with a `timestamp` and a `body` field identifying which service was called.
- **Echo** -- a utility function that returns the request body as-is.

All functions run on Node.js 24 (arm64).

## Prerequisites

- AWS CLI v2, configured with credentials (`aws configure` or environment variables)
- Node.js and npm (for installing router dependencies)
- `zip` and `make`

## Make Targets

| Target | Description |
|---|---|
| `make package` | Build ZIP artifacts for all five functions |
| `make deploy` | Package, create IAM role, deploy all functions with the Gremlin Lambda layer, create API Gateway, and print the URL |
| `make deploy-raw` | Same as `deploy` but without the Gremlin Lambda layer |
| `make info` | Print the ARN of each deployed function and the API Gateway URL |
| `make cleanup` | Delete the API Gateway, all Lambda functions, the IAM role, and local ZIP files |
| `make clean` | Delete local ZIP files only |

## Deploying

### Gremlin Configuration

Before deploying with the Gremlin layer, copy the configuration template into the `secrets` folder and fill in your team credentials:

```sh
cp configuration-template.yaml secrets/configuration.yaml
```

Then edit `secrets/configuration.yaml` and replace the placeholder values for `team_id`, `team_certificate`, and `team_private_key` with the real values for your team. You can download these from your team settings page at https://app.gremlin.com/settings/teams.

### Running the Deploy

```sh
make deploy
```

On first run this will:

1. Build ZIP packages for each function
2. Create an IAM execution role (`ff-examples-execution-role`) with permissions for CloudWatch Logs and Lambda invocation
3. Create all five Lambda functions in `us-west-1`
4. Create an HTTP API Gateway (`ff-examples-api`) wired to the router
5. Print the live URL

On subsequent runs it updates existing resources in place.

To deploy without the Gremlin failure flags layer:

```sh
make deploy-raw
```

### Lambda Layer

The `deploy` target attaches a Lambda layer to every function. By default it automatically resolves the latest version of the Gremlin failure flags layer (`gremlin-lambda-v2-x86_64`) in the configured region by calling `aws lambda list-layer-versions` at deploy time.

To pin a specific version or use a different layer entirely, set `LAYER_ARN`:

```sh
make deploy LAYER_ARN=arn:aws:lambda:us-west-1:123456789012:layer:my-layer:1
```

Or export it as an environment variable:

```sh
export LAYER_ARN=arn:aws:lambda:us-west-1:123456789012:layer:my-layer:1
make deploy
```

When `LAYER_ARN` is set explicitly, the automatic lookup is skipped.

## Testing the Deployment

Once deployed, the API Gateway URL is printed at the end of `make deploy` or available via `make info`.

Visit the root URL in a browser to see the HTML index with links to each service. Or test from the command line:

```sh
# Fetch the index page
curl https://<api-id>.execute-api.us-west-1.amazonaws.com/

# Call each backend service
curl https://<api-id>.execute-api.us-west-1.amazonaws.com/a
curl https://<api-id>.execute-api.us-west-1.amazonaws.com/b
curl https://<api-id>.execute-api.us-west-1.amazonaws.com/c
```

Each service endpoint returns JSON:

```json
{
  "timestamp": "2025-01-15T12:00:00.000Z",
  "body": "Service A called."
}
```

A request to any other path returns a 404. Non-GET methods return a 405.

## IAM Permission Issues

The deploy target creates and manages several AWS resources. If your IAM user or role lacks the necessary permissions you may see `AccessDeniedException` errors. The following permissions are required:

- **STS** -- `sts:GetCallerIdentity` (used to determine the account ID)
- **IAM** -- `iam:CreateRole`, `iam:AttachRolePolicy`, `iam:PutRolePolicy`, `iam:GetRole`, `iam:DeleteRole`, `iam:DetachRolePolicy`, `iam:DeleteRolePolicy`
- **Lambda** -- `lambda:CreateFunction`, `lambda:UpdateFunctionCode`, `lambda:UpdateFunctionConfiguration`, `lambda:GetFunction`, `lambda:DeleteFunction`, `lambda:AddPermission`, `lambda:RemovePermission`, `lambda:GetPolicy`
- **API Gateway v2** -- `apigateway:POST`, `apigateway:GET`, `apigateway:DELETE` (or the broader `apigateway:*` on the relevant resources)
- **CloudWatch Logs** -- the execution role needs `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents`, which are provided by the `AWSLambdaBasicExecutionRole` managed policy attached during deploy

If you encounter permission errors, ask your AWS administrator to grant the actions listed above, or use credentials that have the `AdministratorAccess` managed policy for testing purposes.

## Tearing Down

```sh
make cleanup
```

This removes the API Gateway, all five Lambda functions, the IAM execution role and its policies, and the local ZIP artifacts.
