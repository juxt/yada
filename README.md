# yada

yada is a web library for Clojure. It is a sibling library to [bidi](http://github.com/juxt/bidi) - whereas bidi is based on _routes as data_, yada is based on _resources as data_.

yada takes data declarations and produces a sophisticated Ring
handler.

It has the following features

* Comprehensive HTTP coverage
* Parameter coercion, automatic Swagger support
* Asynch foundation based on [manifold](https://github.com/ztellman/manifold)
* Protocol extensibility

Full documentation for 0.4.3 is available at
[http://yada.juxt.pro](http://yada.juxt.pro) or offline (see below).

Incomplete documentation for 1.0.x is available [here](dev/resources/user-manual.md).

## Installation

For the latest stable release, add the following dependency to your
`project.clj` file

```clojure
[yada "0.4.3"]
```

For the latest release, use the following

```clojure
[yada "1.0.0-20150803.074120-1"]
```

## Status

Please note that being a 0.x.y version indicates the provisional status
of this library, and that features are subject to change.

For the latest 1.0.x release there are expected to be some bugs. Please
feel free to raise GitHub issues.

## Running documentation and examples offline

Although yada is a library, if you clone this repo you can run the documentation and examples from the REPL.

```
cd yada
lein repl
```

Once the REPL starts, type in and run the following :-

```
user> (dev)
dev> (go)
```

Now browse to http://localhost:8090.

## Contributing

If you want to help, please join the discussion group [yada-discuss](https://groups.google.com/forum/#!forum/yada-discuss).

Pull requests are welcome. Please run the test suite and check that all
tests pass prior to submission.

```
$ lein test
```

## Copyright & License

The MIT License (MIT)

Copyright © 2015 JUXT LTD.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
