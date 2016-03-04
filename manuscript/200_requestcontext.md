# The Request Context

When given the HTTP __request__, the handler first creates a __request-context__ and populates it with various values, such as the request and the __resource-model__ that corresponds to the request's URI.

The handler then threads the __request-context__ through a chain of functions, called the __interceptor-chain__. This 'chain' is just a list of functions specified in the __resource-model__ that has been carefully crafted to generate a response that complies fully with HTTP standards. However, as with anything in the resource-model, you can choose to modify it to your exact requirements if necessary.

The functions making up the interceptor-chain are not necessarily executed in a single thread but rather an asynchronous event-driven implementation enabled by a third-party library called manifold.
