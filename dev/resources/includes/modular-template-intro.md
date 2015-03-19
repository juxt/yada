<div class="warning"><p>Forward looking feature... Coming soon!</p></div>

### Trying things out in your own project

Simple examples help to explain a concept but don't show how to create a
complete application.

If you would like to see how an application exposing a yada-based web
API is assembled, you can generate one with JUXT's
[modular](https://github.com/juxt/modular) template system. A template named __yada__ exists that you can reference to generate a Clojure project structure with the following shell command :-

```shell
lein new modular fun-with-yada yada
```

This creates an application directory named __fun-with-yada__ that
demonstrates many of yada's features. (You can replace __fun-with-yada__
with an alternative name of your own invention).

To run and develop the application,
follow the instructions in the `README.md` file that is generated inside
the project directory.

(This requires that you have a fairly recent version (2.3.0+) of Leiningen installed. If you don't have the `lein` command on your system, visit the [Leiningen website](http://leiningen.org) for installation details)
