Until now, when we have constructed bodies we have done so explicitly.

To ensure our compliance with proper HTTP semantics, it is better to return data about the resource and let yada worry about constructing the response body.

The __:resource__ entry allows us to return meta-data about a resource.

The most basic meta-data we can indicate about a resource is whether it exists. We can do this by returning a simple boolean.
