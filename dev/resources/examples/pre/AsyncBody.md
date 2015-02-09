Sometimes the production of the response body involves making requests
to data-sources and other activities which may be _IO-bound_.

In this case, it is possible to return a future, delay, promise or any
other type of deferred value.

Deferred values are built into the default Yada handler, which is built
on Zach Tellman's [manifold](https://github.com/ztellman/manifold)
library.

In this example, we simulate the effect of a long-running operation by
using a future, but promises and delays can be used as well.
