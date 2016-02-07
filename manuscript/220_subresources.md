# Sub-resources

Usually, it is better to declare as much as possible about a resource
prior to directing requests to it. If you do this, your resources will
expose more information and there will be more opportunities to
utilize this information in various ways (perhaps serendipitous
ones). But sometimes it just isn't possible to know everything about a
resource, up front, prior to the request.

A classic example is serving a changing directory of files. Each file
might be a separate resource, identified by a unique URI and have
different possible representations. Unless the directory's contents
are immutable, you should only determine the number and nature of
files contained therein upon the arrival of a request. For this
reason, yada has __sub-resources__. Sub-resources are resources that
are created just-in-time when a request arrives.

## Declaring sub-resources

Any __resource__ can declare that it manages sub-resources by
declaring a __:sub-resource___ entry in its __resource-model__. The
value of a sub-resource is a single-arity function, taking the
__request-context__, that returns a new __resource__, from which a
temporary handler is constructed to serve just the incoming request.

Sub-resources are recursive. A __resource__ that is returned from a
__sub-resource__ function can itself declare that it provides its own
sub-resources, _ad-infinitum_.

## Path info

When routing, it is common for resources that provide sub-resources to
match a set of URIs all starting with a common prefix, and extract the
rest of the path from the request's `:path-info` entry. This is
achieved by declaring a __:path-info?__ entry in the
__resource-model__ set to true.

```clojure
(resource
  {:path-info? true
   :sub-resource
   (fn [ctx]
     (let [path-info (get-in ctx [:request :path-info])]
       (resource {…})))})
```

(For a good example of sub-resources, readers are encouraged to examine
the code for `yada.resources.file-resource` to see how yada serves the
contents of directories.)
