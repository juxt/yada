In the first example, we showed how the `body` entry could be declared
as a constant value. This is fine for static content but often the
body's content depends in some way on the request.

Here, we specify a function. The function takes a single argument `ctx`,
called the _request context_, which contains the original request, content
negotiation results and more.
