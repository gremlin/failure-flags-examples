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
3. Create a Secrets Manager secret (`ff-examples-config`) from `secrets/configuration.yaml` and grant the execution role access to read it
4. Create all five Lambda functions in `us-west-1` with the Gremlin layer attached and the `GREMLIN_CONFIG_ARN`, `GREMLIN_LAMBDA_ENABLED`, `AWS_LAMBDA_EXEC_WRAPPER`, and `HTTP_PROXY` environment variables set. `GREMLIN_CONFIG_ARN_ROLE` can optionally be set.
5. Create an HTTP API Gateway (`ff-examples-api`) wired to the router
6. Print the live URL

On subsequent runs it updates existing resources in place.

To deploy without the Gremlin failure flags layer:

```sh
make deploy-raw
```

This creates the functions without the layer and without the Gremlin environment variables. Running it against functions that `make deploy` already created leaves the previously attached layer in place, but removes the variables that enable it, so the layer does nothing. Run `make cleanup` first if you want the layer gone entirely.

### Lambda Layer

The `deploy` target attaches the Gremlin failure flags layer (`gremlin-lambda-v2-arm64`) to every function. The version is set by `LAYER_VERSION` in the Makefile (currently `32`). Gremlin publishes the latest layer ARNs for every region at https://assets.gremlin.com/packages/gremlin-lambda/LATEST_LAYER_ARNS.

To pin a different version, set `LAYER_VERSION`:

```sh
make deploy LAYER_VERSION=32
```

To use a different layer entirely, set `LAYER_BASE` to its full ARN:

```sh
make deploy LAYER_BASE=arn:aws:lambda:us-west-1:123456789012:layer:my-layer:1
```

Both can also be exported as environment variables before running `make deploy`.

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
- **Secrets Manager** -- `secretsmanager:CreateSecret`, `secretsmanager:PutSecretValue`, `secretsmanager:DescribeSecret`, `secretsmanager:DeleteSecret` (used to store the Gremlin configuration)
- **API Gateway v2** -- `apigateway:POST`, `apigateway:GET`, `apigateway:DELETE` (or the broader `apigateway:*` on the relevant resources)
- **CloudWatch Logs** -- the execution role needs `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents`, which are provided by the `AWSLambdaBasicExecutionRole` managed policy attached during deploy

If you encounter permission errors, ask your AWS administrator to grant the actions listed above, or use credentials that have the `AdministratorAccess` managed policy for testing purposes.

## Tearing Down

```sh
make cleanup
```

This removes the API Gateway, all five Lambda functions, the Secrets Manager secret, the IAM execution role and its policies, and the local ZIP artifacts.
