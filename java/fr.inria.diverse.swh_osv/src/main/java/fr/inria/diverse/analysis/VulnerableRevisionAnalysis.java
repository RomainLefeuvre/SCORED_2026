package fr.inria.diverse.analysis;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.management.RuntimeErrorException;

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

import fr.inria.diverse.Utils.Progress;
import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.model.AffectedRevisionsOpti;
import fr.inria.diverse.model.EventType;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import fr.inria.diverse.model.VulnerableProvenanceMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.tongfei.progressbar.ProgressBar;

/**
 * The goal of this class is to identify new commits that weren't detected by
 * osv.dev
 * refine the detection of the forks by considering metrics such as the amount
 * of new affected commits in such repo, the amount of stars, the amount of
 * forks, etc.
 * 
 * - ratio of number of new commits to the total number of commits
 * - number of range propagatedwith the new commits
 * - comparison of ranges applied to HEADs of forks and forked -> detect forks
 * not taking fixed commits
 * - list of forks that have a vulnerability on HEAD (that vulnerability has a
 * fix that has not been taken by the fork)
 */
public class VulnerableRevisionAnalysis {
    private static final Logger logger = LogManager.getLogger(VulnerableRevisionAnalysis.class);

    private AffectedRevisionsOpti affectedRevisionsOpti;
    Set<SwhVulnerabilityRange> rangesToExclude;
    // Map that contains for each range the set of new vulnerable commits, ie the
    // affected revision in forks
    Map<SwhVulnerabilityRange, AtomicLong> rangeToNumberVulnerableCommits;
    Map<SwhVulnerabilityRange, LongOpenHashSet> rangeToNewVulnerableCommits;
    VulnerableProvenanceMap provenanceMap;
    static ThreadLocal<SwhBidirectionalGraph> threadGraph = new ThreadLocal<>();

    ConcurrentHashMap<SwhVulnerabilityRange, LongArrayList> rangeToOriginId;

    SwhBidirectionalGraph graph;

    private String tmpFolder;

    private String rangeToOriginIdPath;

    // concurrent backup handling
    final int NUM_THREADS = 100;

    public VulnerableRevisionAnalysis(AffectedRevisionsOpti affectedRevisionsOpti,
            VulnerableProvenanceMap provenanceMap,
            SwhBidirectionalGraph graph,
            String outputPath) throws IOException {

        this.affectedRevisionsOpti = affectedRevisionsOpti;
        this.provenanceMap = provenanceMap;
        this.graph = graph;
        this.tmpFolder = Paths.get(outputPath, "tmp/").toString();
        this.rangeToOriginIdPath = Paths.get(outputPath, "rangeToOriginPath.bin").toString();
        ToolBox.createParentIfNeeded(tmpFolder);

        rangeToNewVulnerableCommits = new ConcurrentHashMap<>();
        rangeToNumberVulnerableCommits = new ConcurrentHashMap<>();
        rangeToOriginId = new ConcurrentHashMap<>();
        try (ProgressBar pb = Progress.infoBar("InitializingRangeToNewVulnerableCommit for concurrent access", logger,
                affectedRevisionsOpti.getRanges().size())) {
            affectedRevisionsOpti.getRanges().forEach(range -> {
                pb.step();
                rangeToNewVulnerableCommits.put(range, new LongOpenHashSet());
                rangeToNumberVulnerableCommits.put(range, new AtomicLong());
            });
        }

        populateOrRestoreRangeToOriginId();
        computeRangeToExclude();

        computeRangeToNumberVulnerableCommits(affectedRevisionsOpti);
    }

    public SwhBidirectionalGraph getThreadSafeeGraph() {
        if (threadGraph.get() == null) {
            threadGraph.set(graph.copy());
            logger.info("Copying Graph");
        }
        return threadGraph.get();
    }

    public void populateOrRestoreRangeToOriginId() {
        if (Files.exists(Paths.get(this.rangeToOriginIdPath))) {
            logger.info("rangeToOriginId - restart from backup");
            try {
                this.rangeToOriginId = (ConcurrentHashMap<SwhVulnerabilityRange, LongArrayList>) BinIO
                        .loadObject(this.rangeToOriginIdPath);
            } catch (Exception e) {
                throw new RuntimeException("error while loading backup of " + rangeToOriginIdPath);
            }
        } else {
            populateRangeToOriginId();
        }

    }

    public void populateRangeToOriginId() {
        // Get all the range URL
        logger.info("Get all range url");
        Set<String> rangeURLs = this.affectedRevisionsOpti.getRanges().parallelStream().map(range -> range.getRepoUrl())
                .flatMap(Set::stream).collect(Collectors.toSet());

        LongArrayList originsId = ProgressBar
                .wrap(LongStream.range(0, getThreadSafeeGraph().numNodes()).parallel(),
                        Progress.infoBarBuilder("Fetching origin node", logger))
                .filter(id -> getThreadSafeeGraph().getNodeType(id) == SwhType.ORI)
                .collect(LongArrayList::new,
                        LongArrayList::add, LongArrayList::addAll);

        Map<String, Long> originUrlFiltered = ProgressBar
                .wrap(originsId.longParallelStream(),
                        Progress.infoBarBuilder("Extracting origin url of interests", logger))
                .map(id -> {
                    String url = this.getThreadSafeeGraph().getUrl(id);
                    return url != null ? Map.entry(url, id) : null;
                })
                .filter(Objects::nonNull)
                .filter(mapEntry -> rangeURLs.contains(mapEntry.getKey()))
                .map(mapEntry -> Map.entry(mapEntry.getKey(), mapEntry.getValue()))
                .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));

        this.rangeToOriginId = (ConcurrentHashMap<SwhVulnerabilityRange, LongArrayList>) ProgressBar
                .wrap(this.affectedRevisionsOpti.getRanges().parallelStream(),
                        Progress.infoBarBuilder("Populating rangeToOriginId", logger))
                .map((SwhVulnerabilityRange r) -> {
                    var urlsId = r.getRepoUrl().stream().map(url -> originUrlFiltered.getOrDefault(url, null))
                            .filter(x -> x != null)
                            .collect(Collectors.toCollection(LongArrayList::new));
                    return Map.entry(r, urlsId);
                }).collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));
        try {
            ToolBox.createParentIfNeeded(this.tmpFolder);
            BinIO.storeObject(rangeToNewVulnerableCommits, rangeToOriginIdPath);
        } catch (IOException e) {
            throw new RuntimeException("Exception while saving " + rangeToOriginIdPath);
        }

    }

    // filter out range from which the url of the introcuded in not in the range
    public void computeRangeToExclude() {
        this.rangesToExclude = new HashSet<>();

        for (SwhVulnerabilityRange range : this.affectedRevisionsOpti.getRanges()) {
            List<Long> introducedRevs = range.getRevisionByEvent(EventType.INTRODUCED);
            boolean toExclude = false;
            for (Long introducedRev : introducedRevs) {
                List<String> origins = provenanceMap.getUrls(introducedRev);
                if (origins == null) {
                    logger.warn("No origins associated to " + introducedRev);
                    toExclude = true;
                    break;
                } else {
                    for (String origin : range.getLowerCaseRepoUrl()) {
                        if (!origins.contains(origin)) {
                            logger.warn("Missing origin in SWH for " + origin);
                            toExclude = true;
                        }
                    }

                }
            }
            if (toExclude) {
                this.rangesToExclude.add(range);
            }
        }

        /*
         * this.rangesToExclude = this.affectedRevisionsOpti.getRanges().stream()
         * .filter(range -> !range.getRevisionByEvent(EventType.INTRODUCED).stream()
         * .allMatch(
         * 
         * introduced -> provenanceMap.getUrls(introduced)
         * .containsAll(range.getLowerCaseRepoUrl())
         * 
         * ))
         * .collect(Collectors.toSet());
         * 
         */
    }

    public Map<SwhVulnerabilityRange, LongOpenHashSet> getRangeToNewVulnerableCommits() {
        return rangeToNewVulnerableCommits;
    }

    public VulnerableRevisionAnalysis filterRangeToNewVulnerablecommit(Predicate<SwhVulnerabilityRange> predicate) {
        rangeToNewVulnerableCommits = rangeToNewVulnerableCommits.entrySet().stream()
                .filter(entry -> predicate.test(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return this;

    }

    /**
     * filters out the old vulnerable commits that were already detected by osv.dev
     * 
     * @return
     */
    public VulnerableRevisionAnalysis filterOldVulnerableRevision() {
        logger.info("Filtering provenance to keep only range origin");

        LongOpenHashSet idsToKeep = ProgressBar
                .wrap(this.rangeToOriginId.values().parallelStream(),
                        Progress.infoBarBuilder("Finding ids to keep", logger))
                .flatMapToLong(x -> x.longStream())
                .collect(LongOpenHashSet::new, LongOpenHashSet::add, LongOpenHashSet::addAll);

        provenanceMap.keepOriginId(idsToKeep);
        try (ProgressBar pb = Progress.infoBar("filtering out old revisions", logger, provenanceMap.size())) {

            provenanceMap.revisionParallelStream().forEach(revision -> {
                pb.step();
                // List of origin url associated with the entry key revision,
                // basically the value list of origin internal id index is converted to a list
                // of url
                LongOpenHashSet urlsIdForRev = provenanceMap.getOriginNodeIds(revision);

                // Get the ranges that affect the entry key revision
                List<SwhVulnerabilityRange> allRangesOfRev = affectedRevisionsOpti.getRevisionRange(revision);
                if (urlsIdForRev != null && allRangesOfRev.size() != 0) {
                    // SwhVulnerabilityRange that affect the given revision while this revision is
                    // not on the original repository associated with the range
                    // ie this revision is a newly detected revision in a fork
                    List<SwhVulnerabilityRange> indirectRanges = allRangesOfRev.stream()
                            .filter(range -> !rangesToExclude.contains(range))
                            .filter(range -> {
                                // Get the set of urls associated with the range
                                LongArrayList rangeUrlId = this.rangeToOriginId.get(range);

                                // Set<String> rangeUrls = range.getRepoUrl().stream().map(String::toLowerCase)
                                // .collect(Collectors.toSet());
                                boolean isIndirect = urlsIdForRev.longStream()
                                        .noneMatch(urlId -> rangeUrlId.contains(urlId));

                                return isIndirect;
                            }).collect(Collectors.toList());
                    // Populate the range to new vulnerable commits map
                    /*
                     * if (indirectRanges.size() != 0) {
                     * System.out.println("New vulnerable commit detected for : " +
                     * graph.getSWHID(entry.getKey()));
                     * urlsForRev.stream().forEach(url -> System.out.println(url));
                     * indirectRanges.stream().forEach(range -> System.out.println(range));
                     * System.out.println("====================================");
                     * }
                     */
                    indirectRanges.stream().forEach(range -> {
                        LongOpenHashSet revisionsForRange = rangeToNewVulnerableCommits.get(range);
                        synchronized (revisionsForRange) {
                            revisionsForRange.add((long) revision);
                        }
                    });
                } else {
                    logger.debug("Inconsistency for :" + revision);
                }
            });
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public VulnerableRevisionAnalysis fromBinary(String path) throws ClassNotFoundException, IOException {
        this.rangeToNewVulnerableCommits = (Map<SwhVulnerabilityRange, LongOpenHashSet>) BinIO.loadObject(path);
        return this;
    }

    public VulnerableRevisionAnalysis storeAsBinary(String path) throws IOException {
        ToolBox.createParentIfNeeded(path);
        BinIO.storeObject(rangeToNewVulnerableCommits, path);
        return this;
    }

    public VulnerableRevisionAnalysis storeAsJson(String path) throws IOException {
        if (rangeToNewVulnerableCommits.size() == 0) {
            logger.warn("rangeToNewVulnerableCommits is empty, no json file will be created");
            return this;
        }
        ToolBox.createParentIfNeeded(path);
        File jsonOutputFile = new File(path);

        Gson gson = new Gson();
        try (ProgressBar pb = new ProgressBar("Storing as json",
                rangeToNewVulnerableCommits.size())) {
            PrintWriter pw = new PrintWriter(jsonOutputFile);
            pw.println("[");
            Iterator<Map.Entry<SwhVulnerabilityRange, LongOpenHashSet>> it = this.rangeToNewVulnerableCommits.entrySet()
                    .iterator();
            while (it.hasNext()) {
                pb.step();
                Map.Entry<SwhVulnerabilityRange, LongOpenHashSet> entry = it.next();

                JsonObject currentItem = new JsonObject();
                currentItem.add("range", gson.toJsonTree(entry.getKey()));

                JsonObject metadata = new JsonObject();

                metadata.addProperty("old_vulnerable_revision_number",
                        rangeToNumberVulnerableCommits.get(entry.getKey()).get());

                JsonArray newRevisionArray = new JsonArray();
                entry.getValue().forEach(newRevisionArray::add);

                metadata.add("new_vulnerable_revision", newRevisionArray);

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
        }
        return this;

    }

    private void computeRangeToNumberVulnerableCommits(AffectedRevisionsOpti affectedRevisionsOpti) {

        try (ProgressBar pb = Progress.infoBar("computing number of vulnerable revision", logger,
                provenanceMap.size())) {
            provenanceMap.revisionParallelStream().forEach(revision -> {
                pb.step();
                affectedRevisionsOpti.getRevisionRange(revision).stream()
                        .forEach(range -> {
                            rangeToNumberVulnerableCommits.get(range).incrementAndGet();
                        });
            });
        }

    }

    public static void main(String[] args) throws ClassNotFoundException, IOException, InterruptedException {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String vulnerableProvenancePath = config.getString("vulnerableProvenancePath");
        final String jsonOuputPath = config.getString("jsonOuputPath");
        final String binaryStoredPath = config.getString("binaryStoredPath");
        final String osvLabelResult = config.getString("osvLabelResult");
        logger.info("Loading graph");

        SwhBidirectionalGraph graph = SwhBidirectionalGraph.loadMapped(
                graphPath);
        logger.info("Loading graph message");

        graph.loadMessages();

        logger.info("Loading affected revision");
        AffectedRevisionsOpti affectedRevisionsOpti = (AffectedRevisionsOpti) BinIO.loadObject(osvLabelResult);

        logger.info("Loading provenance Map");

        VulnerableProvenanceMap provenanceMap = new VulnerableProvenanceMap(graphPath, vulnerableProvenancePath, true);
        logger.info("Starting Vulnerable Revision Analysis");
        String outputPath = Paths.get(binaryStoredPath).getParent().toString();

        VulnerableRevisionAnalysis analysis = new VulnerableRevisionAnalysis(affectedRevisionsOpti, provenanceMap,
                graph, outputPath);
        analysis.filterOldVulnerableRevision().storeAsBinary(binaryStoredPath);
        analysis.storeAsJson(jsonOuputPath);

    }

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(VulnerableRevisionAnalysis.class.getName(),
                    "Computes a map of range to origin containing an introduced commit",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("vulnerableProvenancePath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'v',
                                    "vulnerableProvenancePath",
                                    "Path to the folder where the vulnerableProvenancePath will be stored, only used to retrieve the originindexes"),
                            new FlaggedOption("osvLabelResult", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'a',
                                    "osvLabelResult", "Path to the binary of the affectedRevisionOpti"),
                            new FlaggedOption("jsonOuputPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'j',
                                    "jsonOuputPath",
                                    "Path to the json output file of the vulnerableRevisionAnalysis"),
                            new FlaggedOption("binaryStoredPath", JSAP.STRING_PARSER,
                                    JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'n',
                                    "binaryStoredPath",
                                    "Path to the bin file where the rangeToVulnerableCommit map will be stored")
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
}
