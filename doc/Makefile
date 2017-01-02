.PROXY: clean all

all:	clean book.pdf

book.pdf:	book.xml *.xsl
	mkdir -p tmp
	dblatex -s custom.sty -p dblatex-custom.xsl -b xetex -d --tmpdir=tmp $<

book.xml:	*.adoc
	mkdir -p out
	asciidoctor -b docbook5 book.adoc

clean:
	rm -f book.xml book.pdf
	rm -rf tmp
