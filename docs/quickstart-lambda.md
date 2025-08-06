# Quickstart: Failure Flags by Proxy on AWS Lambda

This guide shows how to add the Failure Flags Sidecar to your AWS Lambda function with **no code changes**. It enables both the **dependency proxy** (egress) and **lambda proxy**, so you can begin testing failure modes immediately.

---

## Before Enabling and Connecting the FF Sidecar Proxy

![Request routing without Failure Flags](./images/Without%20FF%20(Lambda).png)

## After Enabling and Connecting the FF Sidecar Proxy

![Request routing with Failure Flags by Proxy](./images/With%20FFbP%20(Lambda).png)

---

## 1. Use Gremlin's Published Lambda Layer

Select the appropriate layer ARN for your region and architecture from the table below:

| Region | x86_64 | Arm64 |
| ------ | ------ | ----- |
| ap-northeast-3 | arn:aws:lambda:ap-northeast-3:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:ap-northeast-3:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| ap-northeast-2 | arn:aws:lambda:ap-northeast-2:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:ap-northeast-2:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| ap-northeast-1 | arn:aws:lambda:ap-northeast-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:ap-northeast-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| ap-south-1 | arn:aws:lambda:ap-south-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:ap-south-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| ap-southeast-1 | arn:aws:lambda:ap-southeast-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:ap-southeast-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| ap-southeast-2 | arn:aws:lambda:ap-southeast-2:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:ap-southeast-2:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| ca-central-1 | arn:aws:lambda:ca-central-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:ca-central-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| eu-north-1 | arn:aws:lambda:eu-north-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:eu-north-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| eu-west-3 | arn:aws:lambda:eu-west-3:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:eu-west-3:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| eu-west-2 | arn:aws:lambda:eu-west-2:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:eu-west-2:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| eu-west-1 | arn:aws:lambda:eu-west-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:eu-west-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| eu-central-1 | arn:aws:lambda:eu-central-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:eu-central-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| sa-east-1 | arn:aws:lambda:sa-east-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:sa-east-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| us-east-1 | arn:aws:lambda:us-east-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:us-east-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| us-east-2 | arn:aws:lambda:us-east-2:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:us-east-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| us-west-1 | arn:aws:lambda:us-west-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:us-west-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |
| us-west-2 | arn:aws:lambda:us-west-2:044815399860:layer:gremlin-lambda-v2-x86_64:2 | arn:aws:lambda:us-west-1:044815399860:layer:gremlin-lambda-v2-arm64:2 |

## 2. Create the Sidecar Config File

Create `ff-config.yaml` with your Gremlin credentials. See the full [configuration guide here](./configuration-guide.md)

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

lambda_proxy_enabled: true
lambda_proxy_port: :5033
```

Replace the team_id, team_certificate, and team_private_key with your actual Gremlin credentials from https://app.gremlin.com/settings/teams.

## 3. Create the Sidecar Config in AWS Secrets Manager

Store your configuration in AWS Secrets Manager:

```bash
aws secretsmanager create-secret \
  --name gremlin-config \
  --description "Gremlin Failure Flags Sidecar Configuration" \
  --secret-string file://ff-config.yaml
```

Note the ARN returned by this command - you'll need it for the next step.

## 4. Update Lambda Function Permissions

Add permissions for the Lambda function to read from Secrets Manager:

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

## 5. Attach the Layer to Your Lambda Function

Add the layer to your Lambda function and configure environment variables (example using us-east-1 x86_64):

```bash
aws lambda update-function-configuration \
  --function-name your-function-name \
  --layers arn:aws:lambda:us-east-1:044815399860:layer:gremlin-lambda-v2-x86_64:2 \
  --environment Variables='{
    "GREMLIN_LAMBDA_ENABLED": "true",
    "GREMLIN_CONFIG_ARN": "arn:aws:secretsmanager:us-east-1:123456789012:secret:gremlin-config-abc123",
    "HTTP_PROXY": "http://localhost:5034",
    "HTTPS_PROXY": "http://localhost:5034",
    "AWS_LAMBDA_EXEC_WRAPPER": /"opt/bootstrap"
  }'
```

Replace the `GREMLIN_CONFIG_ARN` value with the actual ARN returned from step 3. This layer also brings in a bootstrap script. You must set `AWS_LAMBDA_EXEC_WRAPPER` to `/opt/bootstrap` in order to adopt that script. The script sets another environment variable inside your function. That variable `AWS_LAMBDA_RUNTIME_API` must be set to the port where the lambda proxy is running on localhost. If you do not use the included bootstrap script then you must set that environment variable by some other means.

## 6. Result: Automatically Created Failure Flags

Once running, the sidecar will create the following Failure Flags with no code changes:

### For Each Lambda Invocation:

* ingress – Flag on all incoming requests
* http-ingress – Flag on HTTP-specific inbound behavior
* response – Flag when sending responses

### For Each Outgoing Dependency:

* dependency-<hostname> – One flag per remote host your Lambda function contacts

These flags can be triggered from the Gremlin web UI or API to simulate latency, errors, and dropped connections during Lambda execution.

## 7. Testing Your Setup

1. Deploy your Lambda function with the sidecar extension
2. Invoke your function and check the CloudWatch logs for sidecar startup messages
3. Visit the [Gremlin UI](https://app.gremlin.com/failure-flags/list) to see your Lambda's Failure Flags
4. Create your first experiment by triggering a failure on the `lambda` Failure Flag
