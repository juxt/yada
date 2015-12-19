# yada

yada is a web library for Clojure. It is a sibling library to [bidi](http://github.com/juxt/bidi) - whereas bidi is based on _routes as data_, yada is based on _resources as data_.

yada takes data declarations and produces a sophisticated Ring
handler.

It has the following features

* Comprehensive HTTP coverage
* Parameter coercion, automatic Swagger support
* Async foundation based on [manifold](https://github.com/ztellman/manifold)
* Protocol extensibility

Full documentation for the latest 'beta' release is available at
[http://yada.juxt.pro](http://yada.juxt.pro) and offline (see below).

Incomplete documentation for 1.0.x is available [here](dev/resources/user-manual.md).

## Installation

Add the following dependency to your
`project.clj` file

```clojure
[yada "1.0.0"]
```

This 1.0.0 release is the same as the `1.0.0-20150903.093751-9` version, tagged on 2015-09-03. All new development has been taking place on the master branch (currently the 1.1.x series) which has changed the yada API and replaced most of the old protocols with a 'pure' data model. For the latest 'alpha' release in this series, use the following dependency instead :-

```clojure
[yada "1.1.0-20151219.153900-1"]
```

Documentation for the 1.1.x series has not been updated from the 1.0
release. Please refer to the tests and examples for how to use 1.1.x.

For the old 0.4.3 'alpha' release, which is now deprecated, use the following and run the documentation offline (details below).

```clojure
[yada "0.4.3"]
```

Latest master status [![Build Status](https://travis-ci.org/juxt/yada.png)](https://travis-ci.org/juxt/yada)

## Dependencies

yada requires the following :-

- a Java JDK/JRE installation, version 8 or above
- Clojure 1.7.0 (provided)
- Aleph 0.4.1 or above (provided)

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

## Acknowledgments

Thanks to the following people for inspiration, contributions, feedback and
suggestions.

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

## Copyright & License

The MIT License (MIT)

Copyright © 2015 JUXT LTD.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
