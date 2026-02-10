module.exports.handler = async (event) => {
  return {
    statusCode: 200,
    body: JSON.stringify({
      timestamp: new Date().toISOString(),
      body: "Service C called.",
    }),
  };
};
