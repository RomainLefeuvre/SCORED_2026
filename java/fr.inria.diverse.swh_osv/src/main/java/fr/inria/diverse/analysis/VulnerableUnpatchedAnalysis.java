package fr.inria.diverse.analysis;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.type.DateTime;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import fr.inria.diverse.Utils.FolderJsonlCompressor;
import fr.inria.diverse.Utils.Progress;
import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.model.EventType;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import fr.inria.diverse.model.VulnerabilityRange;
import fr.inria.diverse.model.VulnerableProvenanceMap;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.tongfei.progressbar.ProgressBar;

/**
 * The purpose of this class is to study forks that have not applied an existing
 * available patch in their HEAD
 */
public class VulnerableUnpatchedAnalysis {

    Map<SwhVulnerabilityRange, LongOpenHashSet> rangeToNewVulnerableRevisions;
    VulnerableProvenanceMap provenanceMap;
    SwhBidirectionalGraph graph;
    String outputPath;
    String tmpOutputPath;

    Map<SwhVulnerabilityRange, Map<List<String>, LongOpenHashSet>> rangeToUrlToHeadRevisions;

    public VulnerableUnpatchedAnalysis(Map<SwhVulnerabilityRange, LongOpenHashSet> rangeToNewVulnerableRevisions,
            VulnerableProvenanceMap provenanceMap, SwhBidirectionalGraph graph, String outputPath) {
        this.graph = graph;
        this.rangeToNewVulnerableRevisions = rangeToNewVulnerableRevisions;
        this.provenanceMap = provenanceMap;
        this.rangeToUrlToHeadRevisions = new ConcurrentHashMap<>();
        this.outputPath = outputPath;
        this.tmpOutputPath = outputPath + "_tmp.zst";
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws ClassNotFoundException, IOException {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String vulnerableProvenancePath = config.getString("vulnerableProvenancePath");
        final String jsonOuputPath = config.getString("jsonOuputPath");
        final String rangeToVulnRev = config.getString("rangeToVulnRev");

        logger.info("loading range to new vulnerable commits...");
        Map<SwhVulnerabilityRange, LongOpenHashSet> rangeToNewVulnerableRevisions = (Map<SwhVulnerabilityRange, LongOpenHashSet>) BinIO
                .loadObject(rangeToVulnRev);

        logger.info("loading provenanceMap...");
        VulnerableProvenanceMap provenanceMap = new VulnerableProvenanceMap(graphPath, vulnerableProvenancePath, true);

        SwhBidirectionalGraph graph = SwhBidirectionalGraph.loadMapped(graphPath);
        graph.loadCommitterTimestamps();
        logger.info("Starting analysis...");
        VulnerableUnpatchedAnalysis analysis = new VulnerableUnpatchedAnalysis(rangeToNewVulnerableRevisions,
                provenanceMap, graph, jsonOuputPath);

        analysis.filterRanges()
                .filterNonHeadRevisions()
                .formatToUrlToRevision()
                .mergeAndDeleteTmp();

    }

    VulnerableUnpatchedAnalysis mergeAndDeleteTmp() {
        try {
            FolderJsonlCompressor.compressAndDeleteJsonlFiles(this.outputPath, tmpOutputPath, true);
        } catch (IOException e) {
            throw new RuntimeException("Error while merging", e);
        }
        return this;
    }

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(VulnerableUnpatchedAnalysis.class.getName(),
                    "Computes a map of range to origin containing an introduced commit",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("vulnerableProvenancePath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'v',
                                    "vulnerableProvenancePath",
                                    "Path to the folder where the vulnerableProvenancePath will be stored, only used to retrieve the originindexes"),
                            new FlaggedOption("jsonOuputPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'j',
                                    "jsonOuputPath",
                                    "Path to the json file that will contain the ouput of the VulnerableUnpatched Analysis"),
                            new FlaggedOption("rangeToVulnRev", JSAP.STRING_PARSER,
                                    JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'e',
                                    "rangeToVulnRev",
                                    "Path to output of the vulnerableRevisionAnalysis (i.e. a binary file with a Map from range to list of rev)")
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

    public VulnerableUnpatchedAnalysis filterRanges() {

        this.rangeToNewVulnerableRevisions = this.rangeToNewVulnerableRevisions.entrySet().stream().filter(entry -> {

            SwhVulnerabilityRange currentRange = entry.getKey();
            List<Long> fixedRevisions = currentRange.getRevisionByEvent(EventType.FIXED);
            List<Long> last_affectedRevisions = currentRange.getRevisionByEvent(EventType.LAST_AFFECTED);
            if (!last_affectedRevisions.isEmpty() && fixedRevisions.isEmpty()) {
                return false;
            }
            return true;
        }).collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        return this;
    }

    public VulnerableUnpatchedAnalysis storeAsJson(String file) throws IOException {
        ToolBox.createParentIfNeeded(file);
        File jsonOutputFile = new File(file);

        Gson gson = new Gson();

        PrintWriter pw = new PrintWriter(jsonOutputFile);
        Iterator<Map.Entry<SwhVulnerabilityRange, Map<List<String>, LongOpenHashSet>>> it = this.rangeToUrlToHeadRevisions
                .entrySet().iterator();
        try (ProgressBar pb = Progress.infoBar("storing as json", logger,
                this.rangeToNewVulnerableRevisions.size())) {
            while (it.hasNext()) {
                Map.Entry<SwhVulnerabilityRange, Map<List<String>, LongOpenHashSet>> entry = it.next();
                VulnerabilityRange range = new VulnerabilityRange(entry.getKey(),this.graph);
                entry.getValue().forEach((urls, revisionSet) -> {
                    JsonObject currentItem = new JsonObject();
                    
                currentItem.add("range", gson.toJsonTree(range));
                    currentItem.add("urls", gson.toJsonTree(urls));
                    currentItem.add("unpatched_heads",
                            gson.toJsonTree(revisionSet.longStream()
                                    .mapToObj(revision -> this.graph.getSWHID(revision).toString())
                                    .collect(Collectors.toList())));
                    List<Long> dates = revisionSet.longStream().mapToObj(nodeid -> graph.getCommitterTimestamp(nodeid))
                            .collect(Collectors.toList());
                    currentItem.add("timestamps", gson.toJsonTree(dates));

                    pw.println(gson.toJson(currentItem));
                });

                pb.step();
            }
        }
        pw.close();
        return this;
    }

    public VulnerableUnpatchedAnalysis storeAsJson(String file, SwhVulnerabilityRange range,
            Map<List<String>, LongOpenHashSet> unpatched_head) throws IOException {
        if (unpatched_head.size() > 0) {
            ToolBox.createParentIfNeeded(file);
            File jsonOutputFile = new File(file);

            Gson gson = new Gson();
            VulnerabilityRange r = new VulnerabilityRange(range,this.graph);

            PrintWriter pw = new PrintWriter(jsonOutputFile);
            unpatched_head.forEach((urls, revisionSet) -> {
                JsonObject currentItem = new JsonObject();
                currentItem.add("range", gson.toJsonTree(r));

                currentItem.add("urls", gson.toJsonTree(urls));
                currentItem.add("unpatched_heads",
                        gson.toJsonTree(revisionSet.longStream()
                                .mapToObj(revision -> this.graph.getSWHID(revision).toString())
                                .collect(Collectors.toList())));
                List<Long> dates = revisionSet.longStream().mapToObj(nodeid -> graph.getCommitterTimestamp(nodeid))
                        .collect(Collectors.toList());
                currentItem.add("timestamps", gson.toJsonTree(dates));
                pw.println(gson.toJson(currentItem));
            });

            pw.close();
        }
        return this;

    }

    public VulnerableUnpatchedAnalysis formatToUrlToRevision() {
        try (ProgressBar pb = Progress.infoBar("creating range to url to revision map", logger,
                this.rangeToNewVulnerableRevisions.size())) {
            this.rangeToNewVulnerableRevisions
                    .entrySet().parallelStream()
                    .forEach(entry -> {
                        try {
                            pb.step();
                            SwhVulnerabilityRange currentRange = entry.getKey();
                            Map<List<String>, LongOpenHashSet> urlToHeadRevision = new ConcurrentHashMap<>();
                            entry.getValue().forEach(revision -> {
                                List<String> revisionUrls = provenanceMap.getUrls(revision);
                                urlToHeadRevision.putIfAbsent(revisionUrls, new LongOpenHashSet());
                                urlToHeadRevision.get(revisionUrls).add(revision);
                            });

                            this.storeAsJson(this.outputPath + "/" + UUID.randomUUID(), currentRange,
                                    urlToHeadRevision);
                        } catch (IOException e) {
                            throw new RuntimeException("Error while processing range", e);
                        } catch (Exception e) {
                            logger.warn("Error while processing range", e);
                        }
                    });
        }
        return this;

    }

    static ThreadLocal<SwhBidirectionalGraph> threadGraph = new ThreadLocal<>();

    private SwhBidirectionalGraph getThreadSafeeGraph() {
        if (threadGraph.get() == null) {
            threadGraph.set(graph.copy());
        }
        return threadGraph.get();
    }

    private static final Logger logger = LogManager.getLogger(VulnerableUnpatchedAnalysis.class);

    /**
     * filters revisions that have no revision parents i.e. are supposely the HEADs
     * 
     * @return
     */
    public VulnerableUnpatchedAnalysis filterNonHeadRevisions() {

        logger.info("Size before filtering : "
                + numberRevs());
        try (ProgressBar pb = Progress.infoBar("filtering non head revisions", logger,
                this.rangeToNewVulnerableRevisions.size())) {
            this.rangeToNewVulnerableRevisions.values().parallelStream().forEach(revisions -> {
                pb.step();
                revisions.removeIf(this::hasRevisionParents);
            });
        }
        logger.info("Size after filtering : "
                + numberRevs());

        return this;
    }

    private BigInteger numberRevs() {
        BigInteger numberOfRevisions = BigInteger.ZERO;
        List<Integer> tmpList = this.rangeToNewVulnerableRevisions.values().stream()
                .mapToInt(LongOpenHashSet::size)
                .collect(ArrayList<Integer>::new, ArrayList<Integer>::add, ArrayList<Integer>::addAll);

        for (Integer sum : tmpList) {
            numberOfRevisions = numberOfRevisions.add(BigInteger.valueOf(sum));

        }
        return numberOfRevisions;
    }

    private boolean hasRevisionParents(long revNodeId) {
        LazyLongIterator revisionParents = this.getThreadSafeeGraph().predecessors(revNodeId);
        for (long parentNodeId; (parentNodeId = revisionParents.nextLong()) != -1;) {
            if (this.getThreadSafeeGraph().getNodeType(parentNodeId) == SwhType.REV) {
                return true;
            }
        }
        return false;

    }
}
