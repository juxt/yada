We can add any Ring middleware to any handler that has been created by
yada. However, users should be aware of the disadvantages of wrapping
handlers with the wrap-* functions of Ring in asynchronous contexts.
