This example demonstrates a resource that is available in both `application/json` and `application/edn`. We declare these as a set in the __:produces__ entry.

<resource-map/>

Now, let's use the _Accept_ header to request the JSON content-type

<request/>

<response/>

Check the header and the body to see that JSON is returned.
