# Talks

Here are various yada-based talks

* [SkillsMatter 2015](skillsmatter-reagent.html)
* [ClojuTRE 2015](clojutre-2015.html)

## Building

You'll need :-

* Emacs
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
