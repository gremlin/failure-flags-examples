package main

import (
	"context"
	"fmt"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"

	gremlin "github.com/gremlin/failure-flags-go"
)

func handleRequest(ctx context.Context, request events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
	start := time.Now()
	active, impacted, err := gremlin.Invoke(gremlin.FailureFlag{
		Name: `http-ingress`,
		Labels: map[string]string{
			`path`: request.RawPath,
		},
		Logf: gremlin.DefaultLogf,
	})
	if err != nil {
		return events.APIGatewayV2HTTPResponse{}, err
	}
	processingTime := time.Since(start)
	return events.APIGatewayV2HTTPResponse{
		Body:       fmt.Sprintf("{\n  \"active\": %v,\n  \"impacted\": %v,\n  \"processingTime\": %v\n}", active, impacted, processingTime.Milliseconds()),
		StatusCode: 200}, nil
}

func main() {
	lambda.Start(handleRequest)
}
