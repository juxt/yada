While it is possible to explicitly specify the body of a response, doing
so assumes you are prepared to format the content according to the
negotiated content-type.

Instead you can delegate that job to yada, and focus on returning the
logical state of a resource, rather than formatting the content of its
physical representation. Doing this will mean it is easier to support additional content-types.

We provide a `:state` entry in the map we return from the
__:resource__. This is the resource's state.

If there is no __:body__ entry, the state is automatically serialized
into a suitable format (according to the usual rules for determining
the content's type).
