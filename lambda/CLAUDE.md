# apps

This folder contains subfolders with simple NodeJS Lambda functions. These functions demonstrate ingress and egress features of Failure Flags dependency and Lambda proxy modes.

# The Apps

### App: Router

This is a simple Lambda function that serves HTTP requests from an API Gateway and routes those to invocations of one of the other apps (A, B, C, etc). If a request is not for one of those applications, then respond with a 404. It only handles GET requests.

### App: Echo

This is another simple Lambda function that serves HTTP requests from an API Gateway. It always responds with a 200, and echoing the request body as the response body.

### App: ServiceA

Service A always responds with a JSON payload containing two fields: timestamp, and body. Timestamp should always be the timestamp that the request is recieved. Body should always be a static value "Service A called."

### App: ServiceB

Service B always responds with a JSON payload containing two fields: timestamp, and body. Timestamp should always be the timestamp that the request is recieved. Body should always be a static value "Service A called."

### App: ServiceC

Service C always responds with a JSON payload containing two fields: timestamp, and body. Timestamp should always be the timestamp that the request is recieved. Body should always be a static value "Service A called."

## Technology constraints

1. DO NOT use AWS SAM or CDK
2. Minimize dependencies
3. Only use plain JavaScript and the NodeJS standard library where you can
4. Use make and Makefile for packaging and deployment scripting

## Operating Constraints

1. Do not try to execute the Makefile
2. Do not try to deploy the applications
