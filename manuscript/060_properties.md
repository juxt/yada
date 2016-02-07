# Properties

Properties tell us about the current state of a resource, such as
whether the resource exists, or when the resource was last
modified. Properties allow us to determine whether the user agent's
cache of a resource's state is up-to-date.

Sometimes all a resource's properties are constant and can be known
when the resource is defined. More likely the resource's properties
have to be determined by some logic, and often this logic involves
I/O.

Also, if the resource has declared parameters, it can be that
resource's properties depend in some way on these parameters. For
example, the properties of account A may well be different from the
properties of account B.

A resource's properties may also depend on who is making the
request. Your bank account details should only exist if you're the one
accessing them. If I tried to access your bank account details, you'd
want the service to behave differently.

For this reason, a resource's __properties__ declaration in the
__resource-model__ points to a single-arity function that is called by
yada after the request's parameters have been parsed and the
credentials of a caller have been established.

In many cases, it will be necessary to query a database, internal
web-service or equivalent operation involving I/O.

If you use a __properties__ function, anything you return will be
placed in the __:properties__ entry of the __request-context__. Since
the request-context is available when the full response body is
created, you may choose to return the entire state of the resource, in
addition to its properties. This may be sensible if it helps avoid a
second trip to the database.
