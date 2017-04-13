# Make juxt theme
# Need to have a http://sass-lang.com/libsass wrapper

.PHONY: clean all
.DEFAULT: all

WRAPPER = sassc

../static/css/theme/%.css:	%.scss
	$(WRAPPER) -I ./template $< > $@

../static/css/theme/juxt.css: ../static/css/theme/base.css
../static/css/theme/juxt-dark.css: ../static/css/theme/night.css

all:	../static/css/theme/juxt.css ../static/css/theme/juxt-dark.css

clean:
	rm -f ../static/css/theme/juxt.css
	rm -f ../static/css/theme/juxt-dark.css
