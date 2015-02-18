Of course, if you want full control over the rendering of the body, you
can add a __body__ entry. If you do, you can access the state of the resource returned by the __state__ entry which is available from the `ctx` argument.

If you provide a __body__ entry like this, then you should indicate the
content-type that it produces, since it can't inferred automatically (as
you may choose to override it), and no __Content-Type__ header will be set
otherwise.

In this example we use the map option described in [BodyContentTypeNegotiation](#BodyContentTypeNegotiation), but we could have easily chosen to specify the content-type using the __:headers__ option described in [StatusAndHeaders](#StatusAndHeaders).
