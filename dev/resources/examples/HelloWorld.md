Let's look in more detail at the _resource_ you pass to yada's `yada` function to create a Ring handler. This is some kind of object. Let's demonstrate using the example we have already seen.

Suppose the resource is the string "Hello World!".

<handler/>

We can test the effect of this by running the example with the 'Try it' button below. This sends the following request from your browser to a web application.

<request/>

The request is routed to a yada handler which is created passing the string resource as an argument, which generates the response which is displayed below :-

<response/>

The status is set to 200 (OK) and the response body is "Hello World!".

We'll use more examples like that to show different resource maps that demonstrate all the features of yada.
