/*
 * Copyright (c) 2020 The Software Heritage developers
 * See the AUTHORS file at the top-level directory of this distribution
 * License: GNU General Public License version 3, or any later version
 * See top-level LICENSE file for more information
 */

package fr.inria.diverse;

import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.analysis.ConnectedComponentFetcher;
import fr.inria.diverse.model.EventType;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import fr.inria.diverse.model.VulnerabilityRange;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.*;

import com.martiansoftware.jsap.JSAPResult;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class PreProcess extends GraphProcess {
    private static final Logger logger = LogManager.getLogger(PreProcess.class);
    public static String partialResultFileName = "ranges.bin";
    // private final SwhBidirectionalGraph graph;
    protected final SwhBidirectionalGraph graph;
    static ThreadLocal<SwhBidirectionalGraph> threadGraph = new ThreadLocal<>();
    protected final String outfile;
    boolean cache;
    String vulnRangeTemp;// path to the temporary file storing the vulnerability ranges
    // Input, augmented and boun
    protected ArrayList<SwhVulnerabilityRange> swhVulnerabilityRanges;
    private HashMap<String, LongOpenHashSet> cherryPickedMap;

    public SwhBidirectionalGraph getThreadSafeeGraph() {
        if (threadGraph.get() == null) {
            threadGraph.set(graph.copy());
            logger.info("Copying Graph");
        }
        return threadGraph.get();
    }

    public PreProcess(final String graphBasename, final List<VulnerabilityRange> vulnRanges, final String outFile,
            boolean cache, String vulnRangeTemp)
            throws IOException {
        super(graphBasename, vulnRanges, outFile, cache);
        this.vulnRangeTemp = vulnRangeTemp;
        this.cache = cache;
        logger.info("Loading graph " + graphBasename + " ...");
        this.graph = SwhBidirectionalGraph.loadMapped(graphBasename);
        // this.graph.loadCommitterTimestamps();
        ToolBox.createParentIfNeeded(outFile);
        this.outfile = outFile;
        logger.info("Graph loaded.");
    }

    public GraphProcess export() throws IOException {
        ToolBox.createParentIfNeeded(this.outfile);
        BinIO.storeObject(this.swhVulnerabilityRanges, this.outfile);
        return this;
    }

    /**
     * Pre process the vulnerability range :
     * 
     * Compute the list of SWHVulnerabilityRange from a list of VulnerabilityRange
     * (vulnerability_ranges) of a given swh
     * property dataset graph (bidirectional). Convert all the git id of commits to
     * revision node id (internal id of the compressed graph)
     * Retrieve the root revision for ranges that did not have "introduction commit"
     * 
     * @param vulnRanges the raw vulnerability range from osv
     * @throws IOException
     */
    public GraphProcess prepareRanges() throws IOException {
        HashSet<SwhVulnerabilityRange> swhVulnerabilityRangesSet = new HashSet<>();
        String tempPath = this.vulnRangeTemp;
        ToolBox.createParentIfNeeded(tempPath);
        if (cache && ToolBox.checkIfExist(tempPath)) {
            logger.info("----Loading previous swhVulnerabilityRange set - avoid preparation step ----");

            try {
                this.swhVulnerabilityRanges = (ArrayList<SwhVulnerabilityRange>) BinIO.loadObject(tempPath);
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException("Error while reading ranges ", e);
            }

        } else {
            logger.info("----Preprocess of the vulnerability ranges: START----");

            // Convert VulnerabilityRange (referenced with commit hash)
            // To SwhVulnerabilityRange (referenced with webgraph node revision id)
            // Ignore range that contains event that cannot be mapped to the a revision of
            // the graph
            Set<SwhVulnerabilityRange> missingIntroRevRanges = new HashSet<>();
            convertion: for (VulnerabilityRange range : ProgressBar.wrap(vulnRanges, "prepare ranges step 1")) {
                SwhVulnerabilityRange swhVuln = new SwhVulnerabilityRange(range.getVulnerabilityId(),
                        range.getRepoUrl(), range.getSeverity());
                boolean missingIntroRev = false;
                for (var event : range.getEvents().entrySet()) {
                    if (event.getKey().equals("0") && event.getValue() == EventType.INTRODUCED) {
                        // 0 introduction event mean we need to retrieve root !
                        missingIntroRev = true;
                    } else {
                        try {
                            SWHID swhid = new SWHID("swh:1:rev:" + event.getKey());
                            Long nodeId = this.graph.getNodeId(swhid);
                            // Check added from previous version. It seems to have non revision node...
                            // Maybe shifting from String representation of swhid to SWHID correct this
                            // problem
                            // In fact getNodeId(swhid) is checking if SWHID is present in the graph, that
                            // was not the case
                            // When we were using getNodeId(string representing a swhid)
                            if (graph.getNodeType(nodeId) != SwhType.REV) {
                                // logger.warn(graph.getNodeType(nodeId));
                                throw new RuntimeException("Inconsistency, should be a revision" + swhid);
                            }
                            swhVuln.getEvents().put(nodeId, event.getValue());

                        } catch (RuntimeException e) {
                            logger.warn("Inconsistancy, skip" + range + " reason :" + e.getMessage());
                            // If an inconsistancy is detected, skip the entire range
                            continue convertion;
                        }
                    }

                }
                // Handle range duplication, ie range having the same events (type, commit id)
                // but differ in package url
                Optional<SwhVulnerabilityRange> alreadyPresentRange = swhVulnerabilityRangesSet.stream()
                        .filter(x -> x.equalsExceptUrl(swhVuln)).findAny();
                if (alreadyPresentRange.isPresent()) {
                    alreadyPresentRange.get().addRepoUrl(range.getRepoUrl());
                } else {
                    swhVulnerabilityRangesSet.add(swhVuln);
                    if (missingIntroRev) {
                        missingIntroRevRanges.add(swhVuln);
                    }
                }

            }

            try (ProgressBar pb = new ProgressBarBuilder()
                    .setInitialMax(missingIntroRevRanges.size())
                    .setTaskName("prepare ranges step 2 - retrieving root for ranges having no introduced commit")
                    .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                    .build()) {
                // Retrieve roots for repo having no Introduction revision
                missingIntroRevRanges.parallelStream().forEach(swhVulnerabilityRange -> {
                    synchronized (this) {
                        pb.step();
                    }
                    assert (swhVulnerabilityRange.getRevisionByEvent(EventType.INTRODUCED).size() == 0)
                            : "Inconsistency, should have no introduced event at this stage";
                    for (var revisionId : new HashSet<>(swhVulnerabilityRange.getEvents().keySet())) {
                        Set<Long> rootNodeFound = this.getRootRevisions(revisionId);
                        for (Long introducedCandidate : rootNodeFound) {
                            if (swhVulnerabilityRange.containsRevision(introducedCandidate) &&
                                    swhVulnerabilityRange.getType(introducedCandidate) != EventType.INTRODUCED) {
                                logger.warn("Inconsistency, trying to erase non introduced node "
                                        + swhVulnerabilityRange + " skipping");
                                break;
                            }
                            swhVulnerabilityRange.putRevision(introducedCandidate, EventType.INTRODUCED);
                        }
                    }
                });
            }
            this.swhVulnerabilityRanges = new ArrayList<>(swhVulnerabilityRangesSet);
            try {
                ToolBox.createParentIfNeeded(tempPath);
                BinIO.storeObject(this.swhVulnerabilityRanges, tempPath);
            } catch (IOException e) {
                throw new RuntimeException("Error while saving", e);
            }
            logger.info("Ended prep !");
            logger.warn("number of ranges before preprocess: " + vulnRanges.size());
            logger.warn("number of ranges after preprocess: " + this.swhVulnerabilityRanges.size());
        }
        return this;
    }

    /**
     * Retrieve all the root revisions of a given revison
     */
    private Set<Long> getRootRevisions(final long revisionId) {
        SwhBidirectionalGraph graphcopy = this.graph.copy();
        if (graphcopy.getNodeType(revisionId) != SwhType.REV) {
            throw new RuntimeException("revision id " + revisionId + " not a revision");
        }

        final Set<Long> rootRevisions = new HashSet<>();
        final Stack<Long> stack = new Stack<>();
        final HashSet<Long> visited = new HashSet<>();
        stack.add(revisionId);
        visited.add(revisionId);
        while (!stack.isEmpty()) {
            final long currenRevisionId = stack.pop();
            boolean isRootRevision = true;
            final LazyLongIterator it = graphcopy.successors(currenRevisionId);
            for (long nodeId; (nodeId = it.nextLong()) != -1;) {
                if (graphcopy.getNodeType(nodeId) == SwhType.REV) {
                    isRootRevision = false;
                    if (!visited.contains(nodeId)) {
                        stack.push(nodeId);
                        visited.add(nodeId);
                    }
                }
            }
            if (isRootRevision) {
                rootRevisions.add(currenRevisionId);
            }
        }
        return rootRevisions;
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String outdir = config.getString("outdir");
        final String vulnRangesPath = config.getString("vulnRangesPath");
        final String vulnRangeTemp = config.getString("vulnRangeTemp");
        List<VulnerabilityRange> vulns = deserializeVulnRanges(vulnRangesPath);

        final PreProcess pp = new PreProcess(graphPath, vulns, outdir, true, vulnRangeTemp);
        pp.prepareRanges().execute().export();
    }

    @Override
    public GraphProcess execute() {
        logger.info("Computing cherry-picked map ...");
        ConnectedComponentFetcher fetcher = new ConnectedComponentFetcher(this.getThreadSafeeGraph(),
                this.swhVulnerabilityRanges);
        Set<Long> connectedComponent = fetcher.computeNodeInConnectedComponent();

        try {
            this.cherryPickedMap = fetcher.cherryPickCompute(connectedComponent);
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info("extending existing ranges with cherry picks");
        for (SwhVulnerabilityRange swhVuln : this.swhVulnerabilityRanges) {
            List<Long> introducedNodeIds = swhVuln.getRevisionByEvent(EventType.INTRODUCED);
            List<Long> fixedNodeIds = swhVuln.getRevisionByEvent(EventType.FIXED);
            // List<Long> lastAffectedNodeIds =
            // swhVuln.getRevisionByEvent(EventType.LAST_AFFECTED);
            // List<Long> limitNodeIds = swhVuln.getRevisionByEvent(EventType.LIMIT);

            extendIfExist(swhVuln, introducedNodeIds, EventType.INTRODUCED);
            extendIfExist(swhVuln, fixedNodeIds, EventType.FIXED);
            // extendIfExist(swhVuln, lastAffectedNodeIds, EventType.LAST_AFFECTED);
            // extendIfExist(swhVuln, limitNodeIds, EventType.LIMIT);
        }
        return this;
    }

    @Override
    GraphProcess exportRangesToFile(String url) throws IOException {
        try (FileWriter f = new FileWriter(url)) {
            for (SwhVulnerabilityRange swhVuln : this.swhVulnerabilityRanges) {
                f.write(swhVuln.toString() + "\n");
            }
        }
        return this;
    }

    private void extendIfExist(SwhVulnerabilityRange swhVuln, List<Long> introducedNodeIds, EventType event) {
        for (Long rangeNodeId : introducedNodeIds) {
            String currentNodeHash = this.getThreadSafeeGraph().getSWHID(rangeNodeId).toString()
                    .split("swh:1:rev:")[1];
            String shortHash = currentNodeHash.substring(0, 7);
            if (this.cherryPickedMap.keySet().contains(currentNodeHash)
                    || this.cherryPickedMap.keySet().contains(shortHash)) {
                this.cherryPickedMap.getOrDefault(currentNodeHash, this.cherryPickedMap.get(shortHash)).forEach(x -> {
                    if (!swhVuln.getEvents().containsKey(x)) {
                        swhVuln.putRevision(x, event);
                    }
                });
            }
        }

    }

}
