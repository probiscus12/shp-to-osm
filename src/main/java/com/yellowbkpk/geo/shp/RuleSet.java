package com.yellowbkpk.geo.shp;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

import com.yellowbkpk.osm.primitive.Primitive;
import com.yellowbkpk.osm.primitive.PrimitiveTypeEnum;
import com.yellowbkpk.osm.primitive.Tag;

public class RuleSet {
	
	private static Logger log = Logger.getLogger(RuleSet.class.getName());
	
	private RuleSet () {
		super();
	}
	
	public static RuleSet createFromRulesFile (File rulesFile) throws IOException {
		RuleSet ruleSet = readFileToRulesSet(rulesFile);
		return ruleSet;
	}
	
	public static RuleSet createWithCopyTagsPrefix (String allTagsPrefix) {
		RuleSet ruleSet = new RuleSet();
		ruleSet.setUseAllTags(allTagsPrefix);
		return ruleSet;
	}
	
	public static RuleSet createEmptyRuleSet () {
		return new RuleSet();
	}

    private List<Rule> inner = new LinkedList<Rule>();
    private List<Rule> outer = new LinkedList<Rule>();
    private List<Rule> point = new LinkedList<Rule>();
    private List<Rule> line = new LinkedList<Rule>();
    private List<ExcludeRule> excludeRules = new LinkedList<ExcludeRule>();
    private String allTagsPrefix = null;
    
    public void addInnerPolygonRule(Rule r) {
        inner.add(r);
    }
    public void addOuterPolygonRule(Rule r) {
        outer.add(r);
    }
    public void addPointRule(Rule r) {
        point.add(r);
    }
    public void addLineRule(Rule r) {
        line.add(r);
    }
    
    public List<Rule> getInnerPolygonRules() {
        return inner;
    }
    public List<Rule> getOuterPolygonRules() {
        return outer;
    }
    public List<Rule> getPointRules() {
        return point;
    }
    public List<Rule> getLineRules() {
        return line;
    }
    public void addFilter(ExcludeRule rule) {
        excludeRules.add(rule);
    }
    public boolean includes(Primitive w) {
        for (ExcludeRule rule : excludeRules) {
            if(!rule.allows(w)) {
                return false;
            }
        }
        return true;
    }
    public void setUseAllTags(String allTagsPrefix) {
        this.allTagsPrefix = allTagsPrefix;
    }
    public void appendRules(RuleSet existingRules) {
        inner.addAll(existingRules.inner);
        outer.addAll(existingRules.outer);
        point.addAll(existingRules.point);
        line.addAll(existingRules.line);
        excludeRules.addAll(existingRules.excludeRules);
    }
    public void applyLineRules(SimpleFeature feature, String geometryType, List<? extends Primitive> primitives) {
        applyRules(feature, geometryType, primitives, line);
    }
    public void applyOuterPolygonRules(SimpleFeature feature, String geometryType, List<? extends Primitive> primitives) {
        applyRules(feature, geometryType, primitives, outer);
    }
    public void applyInnerPolygonRules(SimpleFeature feature, String geometryType, List<? extends Primitive> primitives) {
        applyRules(feature, geometryType, primitives, inner);
    }
    public void applyPointRules(SimpleFeature feature, String geometryType, List<? extends Primitive> primitives) {
        applyRules(feature, geometryType, primitives, point);
    }

    public void applyRules(SimpleFeature feature, String geometryType, List<? extends Primitive> primitives, List<Rule> rules) {
        if(allTagsPrefix != null) {
            for (Primitive primitive : primitives) {
                applyOriginalTagsTo(feature, geometryType, primitive, allTagsPrefix);
            }
        }
        
        Collection<Property> properties = feature.getProperties();
        for (Property property : properties) {
            String srcKey = property.getType().getName().toString();
            if (!geometryType.equals(srcKey)) {

                Object value = property.getValue();
                if (value != null) {
                    String dirtyOriginalValue = getDirtyValue(value);
                    
                    if (!StringUtils.isEmpty(dirtyOriginalValue)) {
                        String escapedOriginalValue = StringEscapeUtils.escapeXml(dirtyOriginalValue);

                        for (Rule rule : rules) {
                            Tag t = rule.createTag(srcKey, escapedOriginalValue);
                            if (t != null) {
                                for (Primitive primitive : primitives) {
                                    primitive.addTag(t);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private static String getDirtyValue(Object value) {
        String dirtyOriginalValue;
        if (value instanceof Double) {
            double asDouble = (Double) value;
            double floored = Math.floor(asDouble);
            if(floored == asDouble) {
                dirtyOriginalValue = Integer.toString((int) asDouble);
            } else {
                dirtyOriginalValue = Double.toString(asDouble);
            }
        } else {
            dirtyOriginalValue = value.toString().trim();
        }
        return dirtyOriginalValue;
    }

    private static void applyOriginalTagsTo(SimpleFeature feature, String geometryType, Primitive w, String prefix) {
        String prefixPlusColon = "";
        if (!"".equals(prefix)) {
            prefixPlusColon = prefix + ":";
        }
        
        Collection<Property> properties = feature.getProperties();
        for (Property property : properties) {
            String name = property.getType().getName().toString();
            if (!geometryType.equals(name)) {
                Object value = property.getValue();
                if (value != null) {
                    String dirtyOriginalValue = getDirtyValue(value);

                    if (!StringUtils.isEmpty(dirtyOriginalValue)) {
                        String escapedOriginalValue = StringEscapeUtils.escapeXml(dirtyOriginalValue);

                        w.addTag(new Tag(prefixPlusColon + name, escapedOriginalValue));
                    }
                }
            }
        }
    }
    
    /**
     * @param file
     * @return
     * @throws IOException 
     */
    private static RuleSet readFileToRulesSet(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        
        RuleSet rules = new RuleSet();
        
        String line;
        int lineCount = 0;
        while ((line = br.readLine()) != null) {
            lineCount++;
            
            String trimmedLine = line.trim();
            
            // Skip comments
            if(trimmedLine.startsWith("#")) {
                continue;
            }
            
            // Skip empty lines
            if("".equals(trimmedLine)) {
                continue;
            }
            
            String[] splits = line.split(",", 5);
            if (splits.length == 5) {
                String type = splits[0];
                String srcKey = splits[1];
                String srcValue = splits[2];
                String targetKey = StringEscapeUtils.escapeXml(splits[3]);
                String targetValue = StringEscapeUtils.escapeXml(splits[4]);

                Rule r;

                // If they don't specify a srcValue...
                if ("".equals(srcValue)) {
                    srcValue = null;
                }

                if ("-".equals(targetValue)) {
                    r = new Rule(type, srcKey, srcValue, targetKey);
                } else {
                    r = new Rule(type, srcKey, srcValue, targetKey, targetValue);
                }

                log.log(Level.CONFIG, "Adding rule " + r);
                if ("inner".equals(type)) {
                    rules.addInnerPolygonRule(r);
                } else if ("outer".equals(type)) {
                    rules.addOuterPolygonRule(r);
                } else if ("line".equals(type)) {
                    rules.addLineRule(r);
                } else if ("point".equals(type)) {
                    rules.addPointRule(r);
                } else {
                    log.log(Level.WARNING, "Line " + lineCount + ": Unknown type " + type);
                }
            } else if (splits.length == 4) {
                try {
                    PrimitiveTypeEnum type = PrimitiveTypeEnum.valueOf(splits[0]);
                    String action = splits[1];
                    String key = splits[2];
                    String value = splits[3];
        
                    if ("exclude".equals(action)) {
                        ExcludeRule excludeFilter = new ExcludeRule(type, key, value);
                        rules.addFilter(excludeFilter);
                        log.log(Level.CONFIG, "Adding exclude filter " + excludeFilter);
                    }
                } catch(IllegalArgumentException e) {
                    log.log(Level.WARNING, "Skipped line " + lineCount + ": \""
                        + line + "\". Unknown primtive type specified. Unless you're trying to do" +
                        		" an exclude rule, you probably didn't put enough commas in.");
                }
            } else {
                log.log(Level.WARNING, "Skipped line " + lineCount + ": \""
                        + line + "\". Had " + splits.length
                        + " pieces and expected 5 or 3.");
                continue;
            }
        }
        
        return rules;
    }
    
}
