REGION        := us-west-1
PREFIX        := ff-examples
RUNTIME       := nodejs24.x
ARCHITECTURE  := arm64
LAYER_VERSION ?= 9
LAYER_BASE    ?= arn:aws:lambda:$(REGION):044815399860:layer:gremlin-lambda-v2-$(ARCHITECTURE):$(LAYER_VERSION)
ROLE_NAME     := ff-examples-execution-role
API_NAME      := ff-examples-api

TRUST_POLICY := '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
INVOKE_POLICY := '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":"lambda:InvokeFunction","Resource":"*"}]}'

APPS := echo router serviceA serviceB serviceC
ZIPS := $(addsuffix .zip,$(APPS))

.PHONY: package deploy deploy-raw clean cleanup info

package: $(ZIPS)

echo.zip: echo/index.js
	cd echo && zip -r ../$@ index.js

router.zip: router/index.js router/package.json
	cd router && npm install --omit=dev && zip -r ../$@ index.js node_modules/

serviceA.zip: serviceA/index.js
	cd serviceA && zip -r ../$@ index.js

serviceB.zip: serviceB/index.js
	cd serviceB && zip -r ../$@ index.js

serviceC.zip: serviceC/index.js
	cd serviceC && zip -r ../$@ index.js

clean:
	rm -f $(ZIPS)

info:
	@ACCOUNT_ID=$$(aws sts get-caller-identity --query 'Account' --output text 2>/dev/null) && \
		echo "Account: $$ACCOUNT_ID" || echo "Account: unable to determine"; \
	echo "Region:  $(REGION)"; \
	echo "Layer:   $(LAYER_BASE)"; \
	echo ""; \
	echo "Lambda Functions:"; \
	for app in $(APPS); do \
		ARN=$$(aws lambda get-function \
			--function-name $(PREFIX)-$$app \
			--region $(REGION) \
			--query 'Configuration.FunctionArn' \
			--output text 2>/dev/null) && \
			echo "  $$app: $$ARN" || \
			echo "  $$app: not found"; \
	done; \
	echo ""; \
	API_ID=$$(aws apigatewayv2 get-apis \
		--region $(REGION) \
		--query "Items[?Name=='$(API_NAME)'].ApiId | [0]" \
		--output text 2>/dev/null); \
	if [ -n "$$API_ID" ] && [ "$$API_ID" != "None" ]; then \
		echo "API Gateway URL: https://$$API_ID.execute-api.$(REGION).amazonaws.com"; \
	else \
		echo "API Gateway: not found"; \
	fi

cleanup:
	@echo "Deleting API Gateway $(API_NAME)..."; \
	API_ID=$$(aws apigatewayv2 get-apis \
		--region $(REGION) \
		--query "Items[?Name=='$(API_NAME)'].ApiId | [0]" \
		--output text 2>/dev/null); \
	if [ -n "$$API_ID" ] && [ "$$API_ID" != "None" ]; then \
		aws apigatewayv2 delete-api \
			--api-id $$API_ID \
			--region $(REGION) && \
		echo "  Deleted."; \
	else \
		echo "  Not found, skipping."; \
	fi; \
	aws lambda remove-permission \
		--function-name $(PREFIX)-router \
		--statement-id $(API_NAME)-invoke \
		--region $(REGION) 2>/dev/null || true
	@for app in $(APPS); do \
		echo "Deleting $(PREFIX)-$$app..."; \
		aws lambda delete-function \
			--function-name $(PREFIX)-$$app \
			--region $(REGION) 2>/dev/null && \
			echo "  Deleted." || echo "  Not found, skipping."; \
	done; \
	echo "Removing IAM role $(ROLE_NAME)..."; \
	aws iam delete-role-policy \
		--role-name $(ROLE_NAME) \
		--policy-name lambda-invoke 2>/dev/null || true; \
	aws iam detach-role-policy \
		--role-name $(ROLE_NAME) \
		--policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole 2>/dev/null || true; \
	aws iam delete-role \
		--role-name $(ROLE_NAME) 2>/dev/null && \
		echo "  Role deleted." || echo "  Role not found, skipping."
	rm -f $(ZIPS)

deploy: LAYERS_FLAG := --layers $(LAYER_BASE)
deploy: _deploy

deploy-raw: LAYERS_FLAG :=
deploy-raw: _deploy

_deploy: package
	@ACCOUNT_ID=$$(aws sts get-caller-identity --query 'Account' --output text); \
	echo "Using account: $$ACCOUNT_ID"; \
	echo "Ensuring IAM role $(ROLE_NAME) exists..."; \
	if aws iam get-role --role-name $(ROLE_NAME) > /dev/null 2>&1; then \
		echo "  Role already exists."; \
	else \
		echo "  Creating role..."; \
		aws iam create-role \
			--role-name $(ROLE_NAME) \
			--assume-role-policy-document $(TRUST_POLICY) > /dev/null && \
		aws iam attach-role-policy \
			--role-name $(ROLE_NAME) \
			--policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole && \
		aws iam put-role-policy \
			--role-name $(ROLE_NAME) \
			--policy-name lambda-invoke \
			--policy-document $(INVOKE_POLICY) && \
		echo "  Waiting for role propagation..." && \
		sleep 15; \
	fi; \
	ROLE_ARN=$$(aws iam get-role --role-name $(ROLE_NAME) --query 'Role.Arn' --output text); \
	echo "Using role: $$ROLE_ARN"; \
	for app in echo serviceA serviceB serviceC; do \
		echo "Deploying $(PREFIX)-$$app..."; \
		if aws lambda get-function --function-name $(PREFIX)-$$app --region $(REGION) > /dev/null 2>&1; then \
			aws lambda update-function-code \
				--function-name $(PREFIX)-$$app \
				--zip-file fileb://$$app.zip \
				--region $(REGION) > /dev/null && \
			aws lambda wait function-updated \
				--function-name $(PREFIX)-$$app \
				--region $(REGION) && \
			aws lambda update-function-configuration \
				--function-name $(PREFIX)-$$app \
				--runtime $(RUNTIME) \
				$(LAYERS_FLAG) \
				--architectures $(ARCHITECTURE) \
				--region $(REGION) > /dev/null; \
		else \
			aws lambda create-function \
				--function-name $(PREFIX)-$$app \
				--runtime $(RUNTIME) \
				--handler index.handler \
				--role $$ROLE_ARN \
				--zip-file fileb://$$app.zip \
				$(LAYERS_FLAG) \
				--architectures $(ARCHITECTURE) \
				--region $(REGION) > /dev/null; \
		fi; \
		echo "  Done."; \
	done; \
	echo "Deploying $(PREFIX)-router..."; \
	if aws lambda get-function --function-name $(PREFIX)-router --region $(REGION) > /dev/null 2>&1; then \
		aws lambda update-function-code \
			--function-name $(PREFIX)-router \
			--zip-file fileb://router.zip \
			--region $(REGION) > /dev/null && \
		aws lambda wait function-updated \
			--function-name $(PREFIX)-router \
			--region $(REGION) && \
		aws lambda update-function-configuration \
			--function-name $(PREFIX)-router \
			--runtime $(RUNTIME) \
			$(LAYERS_FLAG) \
			--architectures $(ARCHITECTURE) \
			--environment 'Variables={SERVICE_A_FUNCTION=$(PREFIX)-serviceA,SERVICE_B_FUNCTION=$(PREFIX)-serviceB,SERVICE_C_FUNCTION=$(PREFIX)-serviceC}' \
			--region $(REGION) > /dev/null; \
	else \
		aws lambda create-function \
			--function-name $(PREFIX)-router \
			--runtime $(RUNTIME) \
			--handler index.handler \
			--role $$ROLE_ARN \
			--zip-file fileb://router.zip \
			$(LAYERS_FLAG) \
			--architectures $(ARCHITECTURE) \
			--environment 'Variables={SERVICE_A_FUNCTION=$(PREFIX)-serviceA,SERVICE_B_FUNCTION=$(PREFIX)-serviceB,SERVICE_C_FUNCTION=$(PREFIX)-serviceC}' \
			--region $(REGION) > /dev/null; \
	fi; \
	echo "  Done."; \
	echo "Setting up API Gateway $(API_NAME)..."; \
	ROUTER_ARN=$$(aws lambda get-function \
		--function-name $(PREFIX)-router \
		--region $(REGION) \
		--query 'Configuration.FunctionArn' --output text); \
	API_ID=$$(aws apigatewayv2 get-apis \
		--region $(REGION) \
		--query "Items[?Name=='$(API_NAME)'].ApiId | [0]" \
		--output text 2>/dev/null); \
	if [ -n "$$API_ID" ] && [ "$$API_ID" != "None" ]; then \
		echo "  API Gateway already exists."; \
	else \
		echo "  Creating HTTP API..."; \
		API_ID=$$(aws apigatewayv2 create-api \
			--name $(API_NAME) \
			--protocol-type HTTP \
			--target $$ROUTER_ARN \
			--region $(REGION) \
			--query 'ApiId' --output text); \
		echo "  Created."; \
	fi; \
	echo "  Ensuring invoke permission..."; \
	aws lambda remove-permission \
		--function-name $(PREFIX)-router \
		--statement-id $(API_NAME)-invoke \
		--region $(REGION) 2>/dev/null || true; \
	aws lambda add-permission \
		--function-name $(PREFIX)-router \
		--statement-id $(API_NAME)-invoke \
		--action lambda:InvokeFunction \
		--principal apigateway.amazonaws.com \
		--source-arn "arn:aws:execute-api:$(REGION):$$ACCOUNT_ID:$$API_ID/*" \
		--region $(REGION) > /dev/null; \
	echo ""; \
	echo "API Gateway URL: https://$$API_ID.execute-api.$(REGION).amazonaws.com"
