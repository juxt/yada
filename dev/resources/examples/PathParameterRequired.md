We declare a path parameter as being required.

<resource-map/>

But we'll omit to include the parameter in the request URI :-

<request/>

So we should get a response with a 400 status code, indicating that the request was malformed because the required __:account__ parameter wasn't included in the URI path.

<response/>
