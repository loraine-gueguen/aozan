<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
version="1.0">

<xsl:template match="/">
<xsl:decimal-format name="aozan" decimal-separator="." grouping-separator=" "/>
<xsl:decimal-format name="thousand" decimal-separator="." grouping-separator=" "/>

<html>
<head>
  <title>fastqscreen <xsl:value-of select="/ReportFastqScreen/sampleName"/></title>
  <style TYPE="text/css">
    td {
      text-align: center;
      width: 100px;
    }
    th {
        background-color: #234CA5;
        color:WhiteSmoke;
        font-style : bold;
        border: thin solid #6495ed;
        width: 50px;
        font-size: 100%;
    }
    table {
        border: medium solid #000000;
        font-size: 95%;
    }
    body{
        font-family: sans-serif;   
        font-size: 90%;
    }
    h1{
        color:#234CA5;
        font-style : italic;
        font-size : 30px;
    }
    h2{}
    h3{
        color:#B9121B;
        font-style : italic;
    }
	div.footer {
    	background-color: #EEE;
    	border:0;
    	margin:0;
		padding:0.5em;
    	height: 1.3em;
		overflow:hidden;
    	font-size: 100%;
    	font-weight: bold;
    	position:fixed;
    	bottom:0;
    	width:100%;
    	z-index:2;
    }
  </style>
</head>

<body>

  <h3>Detection contamination report</h3>

  <ul>
    <li><b>Run Id: </b> <xsl:value-of select="/ReportFastqScreen/RunId"/></li>
    <li><b>Flow cell: </b> <xsl:value-of select="/ReportFastqScreen/FlowcellId"/></li>
    <li><b>Date: </b> <xsl:value-of select="/ReportFastqScreen/RunDate"/></li>
    <li><b>Instrument S/N: </b> <xsl:value-of select="/ReportFastqScreen/InstrumentSN"/></li>
    <li><b>Instrument run number: </b> <xsl:value-of select="/ReportFastqScreen/InstrumentRunNumber"/></li>
    <li><b>Generated by: </b> <xsl:value-of select="/ReportFastqScreen/GeneratorName"/> version <xsl:value-of select="/ReportFastqScreen/GeneratorVersion"/></li>
    <li><b>Reversion : <xsl:value-of select="/ReportFastqScreen/GeneratorRevision"/></li>
    <li><b>Creation date: </b> <xsl:value-of select="/ReportFastqScreen/ReportDate"/></li>
    <br/>
    <li><b>Project : </b> <xsl:value-of select="/ReportFastqScreen/projectName"/></li>
    <li><b>Sample : </b> <xsl:value-of select="/ReportFastqScreen/sampleName"/></li>
    <li><b>Genome sample : </b> <xsl:value-of select="/ReportFastqScreen/genomeSample"/></li>
  </ul>

  <table border="1">
    <tr>
      <xsl:for-each select="/ReportFastqScreen/Report/Columns/Column">
        <th><xsl:value-of select="@name"/></th>
      </xsl:for-each>
    </tr>
   
   <xsl:for-each select="/ReportFastqScreen/Report/Genomes/Genome">
   <tr>
      <td><xsl:value-of select="@name"/></td>
      <xsl:for-each select="Value">
         <td><xsl:value-of select="format-number(.,'#0.00','aozan')"/> %</td>
      </xsl:for-each>
   </tr>
   </xsl:for-each>
   </table>
   
   <p>
   <ul>
     <li><xsl:value-of select="/ReportFastqScreen/Report/ReadsUnmapped/@name"/> : <xsl:value-of select="format-number(/ReportFastqScreen/Report/ReadsUnmapped,'#0.00','aozan')"/> %</li>
     <li><xsl:value-of select="/ReportFastqScreen/Report/ReadsMappedOneGenome/@name"/> : <xsl:value-of select="format-number(/ReportFastqScreen/Report/ReadsMappedOneGenome,'#0.00','aozan')"/> %</li>
     <li><xsl:value-of select="/ReportFastqScreen/Report/ReadsMappedExceptGenomeSample/@name"/> : <xsl:value-of select="format-number(/ReportFastqScreen/Report/ReadsMappedExceptGenomeSample,'#0.00','aozan')"/> %</li>
   
     <li><xsl:value-of select="format-number(/ReportFastqScreen/Report/ReadsMapped,'# ##0','thousand')"/>&#160;  
       <xsl:value-of select="/ReportFastqScreen/Report/ReadsMapped/@name"/>  / 
       <xsl:value-of select="format-number(/ReportFastqScreen/Report/ReadsProcessed,'# ##0','thousand')"/>&#160;
       <xsl:value-of select="/ReportFastqScreen/Report/ReadsProcessed/@name"/>  </li>
   </ul>
   </p>
   
   <p><a href="http://www.transcriptome.ens.fr/aozan/qc-samples-tests.html#contamination" target="_blank">Contamination detection detail report</a></p>
<div class="footer">
	Site 
	<xsl:element name="a">
		<xsl:attribute name="href"><xsl:value-of select="/ReportFastqScreen/GeneratorWebsite"/></xsl:attribute>Aozan</xsl:element>
	(version <xsl:value-of select="/ReportFastqScreen/GeneratorVersion"/>)
	
</div>
</body>
</html>
</xsl:template>
</xsl:stylesheet>
