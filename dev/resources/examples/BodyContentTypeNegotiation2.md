Now let's try the same thing but with the client setting the `Accept` header to indicate that `text/plain` is preferred.

<resource-map/>

Now we set the __Accept__ header in the request to `text/plain`, to specify that we prefer a response body formatted as plain text.

<request/>

<response/>

Now the server returns the text/plain resource.

Also note that this time the Content-Type header in the response has a value of `text/plain`.
