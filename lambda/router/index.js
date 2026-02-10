const { LambdaClient, InvokeCommand } = require("@aws-sdk/client-lambda");

const lambda = new LambdaClient();

const SERVICE_MAP = {
  "/a": process.env.SERVICE_A_FUNCTION,
  "/b": process.env.SERVICE_B_FUNCTION,
  "/c": process.env.SERVICE_C_FUNCTION,
};

module.exports.handler = async (event) => {
  const method = event.requestContext.http.method;
  const path = event.requestContext.http.path;

  try {
    switch (method) {
      case "GET": {
        if (path === "/") {
          return {
            statusCode: 200,
            headers: { "content-type": "text/html" },
            body: "<html><body><h1>Services</h1><ul>"
              + '<li><a href="/a">Service A</a></li>'
              + '<li><a href="/b">Service B</a></li>'
              + '<li><a href="/c">Service C</a></li>'
              + "</ul></body></html>",
          };
        }

        const functionName = SERVICE_MAP[path];
        if (!functionName) {
          return { statusCode: 404 };
        }

        const command = new InvokeCommand({
          FunctionName: functionName,
          Payload: JSON.stringify(event),
        });
        const response = await lambda.send(command);
        const payload = JSON.parse(Buffer.from(response.Payload).toString());

        return {
          statusCode: payload.statusCode || 200,
          body: payload.body,
        };
      }
      default:
        return { statusCode: 405 };
    }
  } catch (e) {
    return { statusCode: 500 };
  }
};
