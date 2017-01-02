<?xml version='1.0' encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version='1.0'>
  <xsl:param name="tocdepth">2</xsl:param>

  <xsl:param name="xetex.font">
    <xsl:text>\setmainfont{Noto Serif}
    </xsl:text>
    <xsl:text>\setsansfont{Noto Sans}
    </xsl:text>
    <xsl:text>\setmonofont{Droid Sans Mono}
    </xsl:text>
  </xsl:param>

  <xsl:template match="phrase[@role='yada']">
    <xsl:text>
      {\fontspec{RochesterYada}[Path=../fonts/,UprightFont=*]\Large yada}
    </xsl:text>
  </xsl:template>

  <xsl:template match="phrase[@role='yada-large']">
    <xsl:text>
      {\fontspec{RochesterYada}[Path=../fonts/,UprightFont=*]\LARGE yada}
    </xsl:text>
  </xsl:template>

  <xsl:template match="phrase[@role='LaTeX']">
    <xsl:text>\LaTeX\space</xsl:text>
  </xsl:template>


</xsl:stylesheet>
