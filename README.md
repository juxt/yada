# yada

yada is a web library for Clojure, designed to support the creation of production services via HTTP.

It has the following features:

* Standards-based, comprehensive HTTP coverage (content negotiation, conditional requests, etc.)
* Parameter validation and coercion, automatic Swagger support
* Rich extensibility (methods, mime-types, security and more)
* Asynchronous, efficient interceptor-chain design built on [manifold](https://github.com/ztellman/manifold)
* Excellent performance, suitable for heavy production workloads

yada is a sibling library to [bidi](http://github.com/juxt/bidi) - whereas bidi is based on _routes as data_, yada is based on _resources as data_.

The user-manual for the latest (1.x) release is available at
[https://juxt.pro/yada](https://juxt.pro/yada) and offline (see below).

The user-manual is also available as an e-book or PDF, at
[Leanpub](https://leanpub.com/yada).

## Installation

For the latest stable release, add the following dependency to your `project.clj` or `build.boot` file:

```
[yada "1.2.15"]
```

For the latest alpha release, add the following dependency to your `project.clj` or `build.boot` file:

```
[yada "1.3.0-alpha9"]
```


[![Build Status](https://travis-ci.org/juxt/yada.svg?branch=master)](https://travis-ci.org/juxt/yada.svg?branch=master)

## Create a yada handler

Typically, yada handlers are created from a configuation expressed in data.

```clojure
(require '[yada.yada :as yada])

(yada/handler
  {:methods
    {:get
      {:produces "text/html"
       :response "<h1>Hello World!</h1>"}}})
```

This is a simple example, there are a lot more options in yada than can be expressed here, but the approach is the same. The data configuration can be hand-authored, or generated programmatically leading enabling creation of consistent APIs at an industrial scale.

## Dependencies

yada requires the following :-

- a Java JDK/JRE installation, version 8 or above
- Clojure 1.8.0

Support for other web-severs, such as undertow, are on the road-map.

## Future compatibility

If you want to ensure that your code will not break with future releases of yada, you should only use functions from the `yada.yada` namespaces.

You are free to use other public functions in yada, but please be warned that these can and do change between releases.

## Lean yada

By default, yada is batteries-included, bringing in a large number of dependencies.

However, a leaner version of yada is available which cuts out Swagger, swagger-ui, JSON (cheshire), Transit, buddy, core.async, SSE and other fat.

The following differences apply:

- yada doesn't automatically encode/decode JSON bodies, or render JSON as HTML
- no parameter validation or coercion
- no Swagger
- no SSE (currently)
- no JWT
- no Transit

To use the lean (or any other) variant of yada, specify the
appropriate classifier in your `project.clj` or `build.boot` file:

```clojure
[yada/lean "1.2.15"]
```

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

## Troubleshooting FAQ

Q. I'm migrating from a version before yada 1.1 and my async multipart and other uploads are not working, sometimes throwing NullPointerExceptions or other errors.

A. Either use yada's built-in yada.server function or make sure you have started aleph's server with the option `raw-stream? :true`. Previous versions of yada left these settings up to the user but it's _very important_ in yada 1.1 that raw-stream? is set.

## Support

yadarians mostly chat in the [Slack channel](https://clojurians.slack.com/messages/yada) plus there is also [a dedicated Gitter channel](https://gitter.im/juxt/yada) channel

[![Join the chat at https://gitter.im/juxt/yada](https://badges.gitter.im/juxt/yada.svg)](https://gitter.im/juxt/yada?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Also, there is a discussion group [yada-discuss](https://groups.google.com/forum/#!forum/yada-discuss) to discuss ideas.

## Contributing

Feel free to raise GitHub issues on this repository.

Pull requests are welcome. Please run the test suite and check that all
tests pass prior to submission.

```
$ lein test
```

If you want to build and test your own version of yada, you need to be aware how to locally install your own version. Since yada is broken into multiple Maven jars, each with their own version declaration, there is a script that allows you to set the version to whatever you need it to be.

```
$ ./set-version 1.3.0-MS-SNAPSHOT
```

Rather than use `lein install`, you should replace `lein` with `./treelein`.

For example:

```
$ ./treelein install
```

This will install all the yada jars into your local Maven repository.

## Acknowledgments

Thanks to the following people for inspiration, contributions,
feedback and suggestions.

* Malcolm Sparks
* Martin Trojer
* Philipp Meier
* David Thomas Hume
* Zach Tellman
* Stijn Opheide
* Frankie Sardo
* Jon Pither
* Håkan Råberg
* Ernestas Lisauskas
* Thomas van der Veen
* Leandro Demartini
* Craig McCraig of the clan McCraig
* Imre Kószó
* Luo Tian
* Joshua Griffith
* Joseph Fahey
* David Smith
* Mike Fikes
* Brian Olsen
* Stanislas Nanchen
* Nicolas Ha
* Eric Fode
* Leon Mergen
* Greg Look
* Tom Coupland
* Mikkel Gravgaard
* Lucas Lago
* Johannes Staffans
* Michiel Borkent
* James Laver
* Marcin Jekot
* Daniel Compton
* Yoshito Komatsu
* Bor Hodošček
* Ivar Refsdal
* Josh Graham
* Joshua Ballanco
* Steven Harms
* Ryan Smith
* Alexis Lee


Also, see the dependency list. In particular, yada would certainly not
exist without the considerable efforts of those behind the following
libraries.

* Manifold & Aleph - Zach Tellman
* Prismatic Schema - Jason Wolfe (and others)
* Ring-swagger - Tommi Riemann (and others)

## Copyright & License

The MIT License (MIT)

Copyright © 2015-2016 JUXT LTD.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
