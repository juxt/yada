.PROXY: clean all

all:	clean yada-manual.pdf

yada-manual.pdf:	yada-manual.xml *.xsl
	mkdir -p tmp
	dblatex -s custom.sty -p dblatex-custom.xsl -b xetex -d --tmpdir=tmp $<

yada-manual.xml:	*.adoc
	mkdir -p out
	asciidoctor -b docbook5 yada-manual.adoc

clean:
	rm -f yada-manual.xml yada-manual.pdf
	rm -rf tmp
