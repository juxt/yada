This example demonstrates a resource that is available in both `text/html` and `text/plain`. The client sets an accept header, indicating that `text/html` is preferred.

<resource-map/>

We set the __Accept__ header in the request to `text/html`, to specify that we prefer a response body formatted as HTML.

<request/>

<response/>

The body is returned in HTML format. Also note that the Content-Type header in the response has a value of `text/html`.
