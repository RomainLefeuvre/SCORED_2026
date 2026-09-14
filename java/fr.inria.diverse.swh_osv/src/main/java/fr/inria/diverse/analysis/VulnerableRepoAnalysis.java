package fr.inria.diverse.analysis;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import fr.inria.diverse.ProvenanceCompute;
import fr.inria.diverse.RangeAnalyzer;
import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import me.tongfei.progressbar.ProgressBar;

/**
 * This class analyzes the newly detected origins for each range.
 * The goal is to identify all the forks of affected package(osv term).
 * Limitations:
 * - unchanged forks are also detected, and therefor indirectly handled by
 * osv.dev
 * 
 */
public class VulnerableRepoAnalysis {
    final private static Logger logger = LogManager.getLogger(ProvenanceCompute.class.getName());

    public static void main(String[] args) throws ClassNotFoundException {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String vulnerableProvenancePath = config.getString("vulnerableProvenancePath");
        final String rangeToOriginPath = config.getString("rangeToOriginPath");
        final String jsonOuputPath = config.getString("jsonOuputPath");
        final String binaryStoredPath = config.getString("binaryStoredPath");

        try {

            SwhBidirectionalGraph graph = SwhBidirectionalGraph.loadMapped(
                    graphPath);
            Map<SwhVulnerabilityRange, IntOpenHashSet> rangeToOrigin = (ConcurrentHashMap<SwhVulnerabilityRange, IntOpenHashSet>) BinIO
                    .loadObject(
                            rangeToOriginPath);

            List<Long> originIndexes = (List<Long>) BinIO.loadObject(
                    vulnerableProvenancePath + "OriginIndexes.bin");

            VulnerableRepoAnalysis analysis = new VulnerableRepoAnalysis(rangeToOrigin, graph, originIndexes);

            logger.info(
                    analysis.removeEmptyOrigins()
                            .detectMissingOriginalOrigins()
                            .filterOldOrigin()
                            .storeAsBinary(
                                    binaryStoredPath)
                            .storeAsJson(
                                    jsonOuputPath)
                            .statistics());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(VulnerableRepoAnalysis.class.getName(),
                    "Computes a map of range to origin containing an introduced commit",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("vulnerableProvenancePath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'v',
                                    "vulnerableProvenancePath",
                                    "Path to the folder where the vulnerableProvenancePath is stored, only used to retrieve the originindexes"),
                            new FlaggedOption("rangeToOriginPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'o',
                                    "rangeToOriginPath",
                                    "Path to the bin file where the rangeToOrigin map will be stored"),
                            new FlaggedOption("jsonOuputPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'j',
                                    "jsonOuputPath",
                                    "Path to the json output file of the vulnerableRepoAnalysis"),
                            new FlaggedOption("binaryStoredPath", JSAP.STRING_PARSER,
                                    "outputs/VulnerableRepoAnalysis/rangeToNewOrigin.bin",
                                    JSAP.NOT_REQUIRED, 'n',
                                    "binaryStoredPath",
                                    "Path to the bin file where the rangeToNewOrigin map will be stored")
                    });

            config = jsap.parse(args);
            if (jsap.messagePrinted()) {
                System.exit(1);
            }
        } catch (final JSAPException e) {
            e.printStackTrace();
        }
        return config;
    }

    Map<SwhVulnerabilityRange, IntOpenHashSet> rangeToOrigin;
    SwhBidirectionalGraph graph;
    List<Long> originIndexes;

    Set<String> reposUrlToRemember = Set.of("https://github.com/torvalds/linux",
            "https://github.com/tensorflow/tensorflow");

    private Statistics statistics;

    private class Statistics {
        int newOrigins = -1;
        int oldTotalOrigins = -1;
        double averageNewOriginsPerRange;
        double median = -1;
        Map<String, Integer> newOriginsForRepo;

        @Override
        public String toString() {
            return "Statistics [newOrigins=" + newOrigins + ", oldTotalOrigins=" + oldTotalOrigins
                    + ", averageNewOriginsPerRange=" + averageNewOriginsPerRange + ", median=" + median
                    + ", newOriginsForRepo=" + newOriginsForRepo + "]";
        }

    }

    public VulnerableRepoAnalysis(Map<SwhVulnerabilityRange, IntOpenHashSet> rangeToOrigin,
            SwhBidirectionalGraph graph, List<Long> originIndexes) throws IOException {
        this.rangeToOrigin = rangeToOrigin;
        this.graph = graph;
        graph.loadMessages();

        this.statistics = new Statistics();
        statistics.oldTotalOrigins = rangeToOrigin.values().stream().flatMap(origins -> origins.stream())
                .collect(Collectors.toSet()).size();

        this.originIndexes = originIndexes;
    }

    public VulnerableRepoAnalysis storeAsBinary(String path) throws IOException {
        ToolBox.createParentIfNeeded(path);
        BinIO.storeObject(rangeToOrigin, path);
        return this;
    }

    public VulnerableRepoAnalysis removeEmptyOrigins() {
        rangeToOrigin.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return this;
    }

    /**
     * detects for origins that are in the osv but not in the origin map.
     * 
     * @return
     */
    public VulnerableRepoAnalysis detectMissingOriginalOrigins() {
        Set<String> missingOriginalOrigins = new HashSet<>();
        rangeToOrigin.entrySet().stream()
                .forEach(entry -> {
                    Set<String> originalRangeUrls = entry.getKey().getRepoUrl();
                    List<String> missingOrigins = originalRangeUrls.stream().filter(url -> entry.getValue().intStream()
                            .filter(originId -> {
                                String currentUrl = graph.getUrl(originIndexes.get(originId));
                                if (currentUrl == null)
                                    return true;// not filtering it
                                else
                                    return currentUrl.equals(url);
                            })
                            .count() == 0).collect(Collectors.toList());

                    missingOriginalOrigins.addAll(missingOrigins);
                });
        // missingOriginalOrigins.forEach(ori -> System.out.println(ori));
        return this;
    }

    /**
     * filters out the origins that are already present in the original osv.
     */
    public VulnerableRepoAnalysis filterOldOrigin() {
        Set<String> originalOrigins = rangeToOrigin.keySet().stream().map(range -> range.getRepoUrl())
                .flatMap(Set<String>::stream).collect(Collectors.toSet());
        rangeToOrigin.entrySet().stream()
                .forEach(entry -> {
                    Set<String> originalRangeUrls = entry.getKey().getRepoUrl().stream()
                            .collect(Collectors.toSet());

                    entry.setValue(entry.getValue().intStream()
                            .filter(indexOrigin -> {
                                String currentUrl = graph.getUrl(originIndexes.get(indexOrigin));
                                if (currentUrl == null)
                                    return false;
                                else
                                    return !(originalRangeUrls.contains(currentUrl)
                                            || originalOrigins.contains(currentUrl));
                            }).collect(IntOpenHashSet::new, IntOpenHashSet::add, IntOpenHashSet::addAll));
                });
        return this;
    }

    /**
     * prints the following statistics:
     * - number of new origins
     * - number of old origins
     * - average number of new origins per range
     * - number of new origins for specific repositories(chrome, firefox, linux,
     * etc.)
     */
    public Statistics statistics() {
        statistics.newOrigins = rangeToOrigin.values().stream().flatMap(origins -> origins.stream())
                .collect(Collectors.toSet()).size();
        statistics.averageNewOriginsPerRange = statistics.newOrigins / rangeToOrigin.size();
        statistics.median = rangeToOrigin.values().stream().mapToInt(IntOpenHashSet::size).sorted()
                .skip(rangeToOrigin.size() / 2).limit(1).average().getAsDouble();


        return this.statistics;
    }

    public VulnerableRepoAnalysis storeAsJson(String file) throws IOException {
        ToolBox.createParentIfNeeded(file);
        File jsonOutputFile = new File(file);

        Gson gson = new Gson();

        PrintWriter pw = new PrintWriter(jsonOutputFile);
        pw.println("[");
        Iterator<Map.Entry<SwhVulnerabilityRange, IntOpenHashSet>> it = this.rangeToOrigin.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SwhVulnerabilityRange, IntOpenHashSet> entry = it.next();

            JsonObject currentItem = new JsonObject();
            currentItem.add("range", gson.toJsonTree(entry.getKey()));

            JsonObject metadata = new JsonObject();

            JsonArray newUrlArray = new JsonArray();
            entry.getValue().forEach(url -> {
                newUrlArray.add(graph.getUrl(originIndexes.get(url)));
            });
            metadata.add("new_origin_urls", newUrlArray);

            currentItem.add("metadata", metadata);

            pw.print(gson.toJson(currentItem));
            if (it.hasNext()) {
                pw.println(",");
            } else {
                pw.println();
            }

        }
        pw.println("]");
        pw.close();
        return this;
    }

}
