In this example we want to demonstrate what happens if there is an error extracting a required parameter because it cannot be coerced into the required type.

We want __account__ to be extracted as a `java.lang.Long`

<resource-map/>

In the request we provide the string `"wrong"` in the place where the
account number is expected.

<request/>

When we try the request, the server fails to coerce the string to a
valid `java.lang.Long` and responds with a 400 error, indicating to the
user agent that the URI was badly formed.

<response/>
