# Talks

Here are various yada-based talks

* [SkillsMatter 2015](skillsmatter-reagent.html) - a bit tenuous, but contains an early sneak-peek of the yada console
* [ClojuTRE 2015](clojutre-2015.html) - my first yada talk, a bit dated and describes the olde protocol-based yada.
* [Skills Matter Clojure eXchange 2015 - Thomas van der Veen](clojurex-2015-thomas.html) - Thomas's talk on creating a phonebook with Compojure
* [Skills Matter Clojure eXchange 2015 - Malcolm Sparks](clojurex-2015-malcolm.html) - My follow-up to Thomas's talk. Reveals the pure-data yada.
* [f(by) Minsk 2016 - Malcolm Sparks](fby-2016.html) - Talk about yada at f(by)

## Building

I don't have time to write presentations. That's why I use
org-mode. If you want to build these from scratch, you'll need :-

* Emacs (a recent version with org-mode built in)
* [org-reveal](https://github.com/yjwen/org-reveal)

For an up-to-date version of org-reveal, the best advice is to clone a
local copy and add the following to your Emacs configuration.

```elisp
(add-to-list 'load-path "~/src/org-reveal")
(require 'ox-reveal)
```

Now load up one of the talks and run `C-c C-e R R` to generate the talk output HTML.

## Theme tweaking

You'll need :-

* GNU make
* A [libsass](http://sass-lang.com/libsass) wrapper (e.g. `sassc`)

If you need to tweak the reveal.js theme in `themes/juxt.scss`, then run
`make` in the `theme` directory to build the final CSS.
