package com.yellowbkpk.geo.shp;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.yellowbkpk.geo.glom.GlommingFilter;
import com.yellowbkpk.osm.output.OSMChangeOutputter;
import com.yellowbkpk.osm.output.OSMOldOutputter;
import com.yellowbkpk.osm.output.OSMOutputter;
import com.yellowbkpk.osm.output.OutputFilter;
import com.yellowbkpk.osm.output.SaveEverything;

/**
 * @author Ian Dees
 * 
 */
public class Main {
    
    private static Logger log = Logger.getLogger(Main.class.getName());

    private static final String GENERATOR_STRING = "shp-to-osm 0.7";

    private static String OPT_SHAPEFILE = "shapefile";
    private static String OPT_RULESFILE = "rulesfile";
    private static String OPT_OSMFILE = "osmfile";
    private static String OPT_OUTDIR = "outdir";
    private static String OPT_MAXNODES = "maxnodes";
    private static String OPT_OUTPUTFORMAT = "outputFormat";
    private static String OPT_GLOMKEY = "glomKey";
    private static String OPT_COPYTAGS = "copyTags";
    private static String OPT_T = "t";
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        
        CommandLineParser parser = new GnuParser();
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt(OPT_SHAPEFILE)
                .withDescription("Path to the input shapefile.")
                .withArgName("SHPFILE")
                .hasArg()
                .isRequired()
                .create());
        options.addOption(OptionBuilder.withLongOpt(OPT_RULESFILE)
                .withDescription("Path to the input rules file.")
                .withArgName("RULESFILE")
                .hasArg()
                .create());
        options.addOption(OptionBuilder.withLongOpt(OPT_OSMFILE)
                .withDescription("Prefix of the output file name.")
                .withArgName("OSMFILE")
                .hasArg()
                .isRequired()
                .create());
        options.addOption(OptionBuilder.withLongOpt(OPT_OUTDIR)
                .withDescription("Directory to output to. Default is working dir.")
                .withArgName("OUTDIR")
                .hasArg()
                .create());
        options.addOption(OptionBuilder.withLongOpt(OPT_MAXNODES)
                .withDescription("Maximum elements per OSM file.")
                .withArgName("nodes")
                .hasArg()
                .create());
        options.addOption(OptionBuilder.withLongOpt(OPT_OUTPUTFORMAT)
                .withDescription("The output format ('osm' or 'osmc' (default)).")
                .withArgName("format")
                .hasArg()
                .create());
        options.addOption(OptionBuilder.withLongOpt(OPT_GLOMKEY)
                .withDescription("The key to 'glom' on. Read the README for more info.")
                .withArgName("key")
                .hasArg()
                .create());
        options.addOption(OptionBuilder.withLongOpt(OPT_COPYTAGS)
                .withDescription("Copy all shapefile attributes to OSM tags verbatim, with an optional prefix.")
                .withArgName("prefix")
                .hasOptionalArg()
                .create());
        options.addOption(OPT_T, false, "Keep only tagged ways");
        
        boolean keepOnlyTaggedWays = false;
        try {
            CommandLine line = parser.parse(options, args, false);
            
            if(line.hasOption(OPT_T)) {
                keepOnlyTaggedWays = true;
            }
            
            if(!line.hasOption(OPT_SHAPEFILE) || !line.hasOption(OPT_RULESFILE) || !line.hasOption(OPT_OSMFILE)) {
                System.out.println("Missing one of the required file paths.");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("java -cp shp-to-osm.jar", options, true);
                System.exit(-1);
            }
            
            File shpFile = new File(line.getOptionValue(OPT_SHAPEFILE));
            if(!shpFile.canRead()) {
                System.out.println("Could not read the input shapefile.");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("java -cp shp-to-osm.jar", options, true);
                System.exit(-1);
            }
            
            final String filePrefix = line.getOptionValue(OPT_OSMFILE);
            String rootDirStr;
            if (line.hasOption(OPT_OUTDIR)) {
                rootDirStr = line.getOptionValue(OPT_OUTDIR);
            } else {
                rootDirStr = ".";
            }
            File rootDirFile = new File(rootDirStr);
            if (!rootDirFile.exists() || !rootDirFile.isDirectory()) {
                System.err.println("Specified outdir is not a directory: \"" + rootDirStr + "\".");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("java -cp shp-to-osm.jar", options, true);
                System.exit(-1);
            }
            
            RuleSet ruleSet = null;
            
            if (line.hasOption(OPT_RULESFILE)) {
                File rulesFile = new File(line.getOptionValue(OPT_RULESFILE));
                if (!rulesFile.canRead()) {
                    System.out.println("Could not read the input rulesfile.");
                    HelpFormatter formatter = new HelpFormatter();
                    formatter.printHelp("java -cp shp-to-osm.jar", options, true);
                    System.exit(-1);
                }
                
                ruleSet = RuleSet.createFromRulesFile(rulesFile);
            }
            
            boolean useAllTags = line.hasOption(OPT_COPYTAGS);
            if (useAllTags) {
                String allTagsPrefix = line.getOptionValue(OPT_COPYTAGS, "");
                if (ruleSet != null) {
                	ruleSet.setUseAllTags(allTagsPrefix);
                } else {
                	ruleSet = RuleSet.createWithCopyTagsPrefix(allTagsPrefix);
                }
            }
            
            if (ruleSet == null) {
            	ruleSet = RuleSet.createEmptyRuleSet();
            }
            
            boolean shouldGlom = false;
            String glomKey = null;
            if(line.hasOption(OPT_GLOMKEY)) {
                glomKey = line.getOptionValue(OPT_GLOMKEY);
                shouldGlom = true;
                System.out.println("Will attempt to glom on key \'" + glomKey + "\'.");
            }

            OSMOutputter outputter = new OSMChangeOutputter(rootDirFile, filePrefix, GENERATOR_STRING);
            if(line.hasOption(OPT_OUTPUTFORMAT)) {
                String type = line.getOptionValue(OPT_OUTPUTFORMAT);
                if("osm".equals(type)) {
                    outputter = new OSMOldOutputter(rootDirFile, filePrefix, GENERATOR_STRING);
                }
                
                if(shouldGlom) {
                    OutputFilter glomFilter = new GlommingFilter(glomKey);
                    outputter = new SaveEverything(outputter).withFilter(glomFilter);
                }
            } else {
                System.err.println("No output format specified. Defaulting to osmChange format.");
            }
            
            int maxNodesPerFile = 50000;
            if(line.hasOption(OPT_MAXNODES)) {
                String maxNodesString = line.getOptionValue(OPT_MAXNODES);
                try {
                    maxNodesPerFile = Integer.parseInt(maxNodesString);
                } catch(NumberFormatException e) {
                    System.err.println("Error parsing max nodes value of \"" + maxNodesString
                            + "\". Defaulting to 50000.");
                }
            }
            outputter.setMaxElementsPerFile(maxNodesPerFile);
            
            ShpToOsmConverter conv = new ShpToOsmConverter(shpFile, ruleSet, keepOnlyTaggedWays, outputter);
            conv.convert();
        } catch (IOException e) {
            log.log(Level.WARNING, "Error reading rules file.", e);
        } catch (ParseException e) {
            System.err.println("Could not parse command line: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -cp shp-to-osm.jar", options, true);
        } catch (ShpToOsmException e) {
            log.log(Level.SEVERE, "Error creating OSM data from shapefile.", e);
        }
        
    }

    
}