PUT a resource. The resource-map returns a resource with an etag which
matches the value of the 'If-Match' header in the request. This means
the PUT can proceed.

> If-Match is most often used with state-changing methods (e.g., POST, PUT, DELETE) to prevent accidental overwrites when multiple user agents might be acting in parallel on the same resource (i.e., to the \"lost update\" problem).
