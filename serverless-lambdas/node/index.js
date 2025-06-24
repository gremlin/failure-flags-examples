const http = require('http');
// Note: you must bring in the failure-flags library
const gremlin = require('@gremlin/failure-flags');

function timeout(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

module.exports.handler = async (event) => {
  start = Date.now();

  // Note: This is an example of a line you need to add to 
  // your application code for each failure flag.
  await gremlin.invokeFailureFlag({
    name: 'http-ingress', // name of the failure flag.
    labels: { 
      method: event.requestContext.http.method, 
      path: event.requestContext.http.path 
    }}); // use this map to qualify on request input.

  return {
    statusCode: 200,
    body: JSON.stringify(
      {
        processingTime: Date.now() - start,
        timestamp: event.requestContext.time,
      },
      null,
      2
    ),
  };
};
