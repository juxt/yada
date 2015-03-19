Return a 503 with a retry header, this time using async.

Imagine the situation where you asked to implement a resource that must limit the number of requests. This is known as _rate limiting_. Perhaps the policy is to limit the requests, per IP address, to 1000 per minute.

One approach would be to stream these requests into an event-processor
which would count how many requests had been received from the same IP
address across a timed window. If too many requests had been received,
the algorithm could respond with a number indicating the number of
seconds in the future when new requests would be considered.

By using promises, this potentially difficult rate-limiting algorithm
can be separated from the API service itself.

<resource-map/>

<request/>

<response/>

It might also be useful to wait for some short period of time before
responding with the 503 status code. This would help provide
back-pressure to the user-agent, especially if the number of connections
from it were also limited at the OS level, which would help prevent any
given user-agent from causing excess load on the service.
