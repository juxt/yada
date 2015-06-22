In this example, we set the __:authorization__ entry to false.

<handler/>

This means that no access is allowed, by anyone, at any time.

<request/>

This is somewhat unusual, but we might decide to make an area of our
website or part of our API inaccessible to all. For instance, the
resource might not be ready for public consumption, or we need to forbid
access to it for some reason (although it would be
better to return a Service Unavailable 503 status code in this case).

A 403 (Forbidden) response will be returned for any request.

<response/>
