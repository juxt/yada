Let's look in more detail at the _resource map_ you pass to yada's `yada` function to create a Ring handler. This map is an ordinary Clojure map. Let's demonstrate using the example we have already seen.

Suppose the __resource map__ contains a constant __:body__ value of "Hello World!".

<resource-map/>

We can test the effect of this by running the example with the 'Try it' button below. This sends the following request from your browser to a web application.

<request/>

The request is routed to a yada handler which is configured with the resource map above, which generates the response which is displayed below :-

<response/>

The status is set to 200 (OK) and the response body is "Hello World!".

We'll use more examples like that to show different resource maps that demonstrate all the features of yada.
