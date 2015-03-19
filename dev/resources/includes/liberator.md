### Déjà vu?

Readers who know [Liberator](http://clojure-liberator.github.io) will be
familiar with the concept of a _resource map_. A resource map defines how
a handler should respond to a request.

While yada and Liberator have a lot in common conceptually, there are
significant differences in both design and implementation. In
particular, the resource maps are incompatible. There is also no
flow-chart exposed in yada.

The design of yada is heavily based on experience with implementing web
APIs with Liberator. You could use Liberator to serve some web
resources, and yada to serve others, but generally Liberator and yada
aren't meant to be combined.
