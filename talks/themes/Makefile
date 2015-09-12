# Make juxt theme
# Need to have a http://sass-lang.com/libsass wrapper

.PHONY: clean all

WRAPPER = sassc

../static/css/theme/%.css:	%.scss
	$(WRAPPER) -I ./template $< > $@

all:	../static/css/theme/juxt.css

clean:
	rm -f ../static/css/theme/juxt.css
