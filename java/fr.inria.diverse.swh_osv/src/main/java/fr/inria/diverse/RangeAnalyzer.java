package fr.inria.diverse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDBException;

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
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import me.tongfei.progressbar.ProgressBar;

public class RangeAnalyzer {

    final private static Logger logger = LogManager.getLogger(RangeAnalyzer.class.getName());

    /**
     * Computes a mapping from a range to all the origin related.
     * 
     * @param vulnerableProvenance the map containing the provenance of each
     *                             vulnerable commit
     * @param affectedRevisions    the AffectedRevision map containing <revision ->
     *                             Range>
     * @return a mapping from a range to all the origin related to the it.
     */
    public Map<SwhVulnerabilityRange, IntOpenHashSet> computeOriginsForRangesV1(
            HashMap<Long, IntOpenHashSet> vulnerableProvenance,
            AffectedRevisionsOpti affectedRevisions) {
        ConcurrentHashMap<SwhVulnerabilityRange, IntOpenHashSet> rangeToAllOrigins = new ConcurrentHashMap<>();

        try (ProgressBar pb = new ProgressBar("Computing the range to origin hashmap",
                vulnerableProvenance.size())) {

            vulnerableProvenance.forEach((rev, origins) -> {
                List<SwhVulnerabilityRange> currentRangesForRev = affectedRevisions.getRevisionRange(rev);
                pb.step();
                currentRangesForRev.parallelStream().forEach(range -> {
                    IntOpenHashSet originsOfRange = rangeToAllOrigins.getOrDefault(rangeToAllOrigins,
                            new IntOpenHashSet());
                    originsOfRange.addAll(origins);
                    rangeToAllOrigins.put(range, originsOfRange);
                });
            });
        }
        return rangeToAllOrigins;
    }

    public ConcurrentHashMap<SwhVulnerabilityRange, IntOpenHashSet> computeOriginsForRangesV2(
            VulnerableProvenanceMap vulnerableProvenance,
            List<SwhVulnerabilityRange> vulns) throws IOException {
        ConcurrentHashMap<SwhVulnerabilityRange, IntOpenHashSet> rangeToAllOrigins = new ConcurrentHashMap<>();

        try (ProgressBar pb = Progress.infoBar("Computing the range to origin hashmap", logger, vulns.size())) {

            vulns.parallelStream().forEach(vuln -> {
                pb.step();
                IntOpenHashSet allOriginOfRange = new IntOpenHashSet();
                vuln.getEvents().entrySet().stream()
                        .filter((Map.Entry<Long, EventType> entry) -> {
                            return entry.getValue() == EventType.INTRODUCED;
                        })
                        .map((Map.Entry<Long, EventType> entry) -> {
                            return entry.getKey();
                        })
                        .forEach(commitNodeId -> {
                            if (vulnerableProvenance.containsKey(commitNodeId)) {
                                allOriginOfRange.addAll(vulnerableProvenance.get(commitNodeId));
                            } else {
                                logger.warn("Skipping " + commitNodeId + " not in the vulnerable provenance map");
                            }
                            ;
                        });

                rangeToAllOrigins.put(vuln, allOriginOfRange);
            });
        }

        return rangeToAllOrigins;
    }

    public static void main(String[] args) throws ClassNotFoundException, IOException, RocksDBException {

        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String allRangesPath = config.getString("allRanges");
        final String vulnerableProvenancePath = config.getString("vulnerableProvenancePath");
        final String rangeToOriginPath = config.getString("rangeToOriginPath");

        RangeAnalyzer rangeAnalyzer = new RangeAnalyzer();
        logger.info("loading ProvenanceMap");
        VulnerableProvenanceMap map = new VulnerableProvenanceMap(graphPath, vulnerableProvenancePath, true);

        List<SwhVulnerabilityRange> allRanges = ((List<SwhVulnerabilityRange>) BinIO.loadObject(allRangesPath));

        Map<SwhVulnerabilityRange, IntOpenHashSet> rangeToOrigins = rangeAnalyzer
                .computeOriginsForRangesV2(map, allRanges);

        ToolBox.createParentIfNeeded(rangeToOriginPath);
        BinIO.storeObject(rangeToOrigins, rangeToOriginPath);
    }

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(RangeAnalyzer.class.getName(),
                    "Computes a map of range to origin containing an introduced commit",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("allRanges", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'r',
                                    "allRanges",
                                    "Path to the binary containing the list of all Ranges(List<SwhVulnerabilityRange>)"),
                            new FlaggedOption("vulnerableProvenancePath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'v',
                                    "vulnerableProvenancePath",
                                    "Path to the folder where the vulnerableProvenancePath is stored"),
                            new FlaggedOption("rangeToOriginPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'o',
                                    "rangeToOriginPath",
                                    "Path to the bin file where the rangeToOrigin map will be stored")
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
