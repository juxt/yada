Let's pretend we will allow access to anyone who puts a particular value in the path of the request. We'll say that "oak" is the magic value that grants access.

We [declare a parameter](#Parameters) called __:secret__ that we configure to be extracted by
the bidi library when dispatching the request.

This time, our __:authorization__ entry is a function that extracts the parameter from the context and compares it with "oak". If it returns true, we'll be granted access to the resource.

<resource-map/>

Let's see this in action, first by specifying the wrong password of "ash".

<request/>

<response/>

We get a 403, which means we're forbidden to access this resource.
