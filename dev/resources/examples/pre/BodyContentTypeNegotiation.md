HTTP has a content negotiation feature which allows a user agent
(browser, device) and a web server to establish the best representation
for a given resource. This may include the mime-type of the content, its
language, charset and transfer encoding.

We shall focus on the mime-type of the content.

There are multiple ways to indicate which content-types can be provided by an implementation. However, as we are discussing the __:body__ entry, we'll first introduce how the implementation can use a map between content-types and bodies.

This example demonstrates a resource that is available in both `text/html` and `text/plain`. The client sets an accept header, indicating that `text/html` is preferred.
