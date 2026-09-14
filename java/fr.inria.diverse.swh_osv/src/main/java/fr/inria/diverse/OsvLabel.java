/*
 * Copyright (c) 2020 The Software Heritage developers
 * See the AUTHORS file at the top-level directory of this distribution
 * License: GNU General Public License version 3, or any later version
 * See top-level LICENSE file for more information
 */

package fr.inria.diverse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDBException;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;

import com.martiansoftware.jsap.JSAPResult;

import fr.inria.diverse.Utils.Progress;
import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.model.AffectedRevisionsOpti;
import fr.inria.diverse.model.EventType;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongStack;
import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

public class OsvLabel extends OsvLabelAbstract {
    private static final Logger logger = LogManager.getLogger(OsvLabel.class);
    public static String resultPath = "result.bin";
    public static String resultTmpPath = "tmp";
    public static int THREAD_NB = 120;
    // Label
    AffectedRevisionsOpti affectedRevisions;

    public AffectedRevisionsOpti getAffectedRevisions() {
        return affectedRevisions;
    }

    public OsvLabel(final String graphBasename, final String vulnRanges, final String outdir, boolean cache)
            throws IOException, ClassNotFoundException {
        super(graphBasename, vulnRanges, outdir, cache);
        affectedRevisions = new AffectedRevisionsOpti(this.swhVulnerabilityRanges);

    }

    @Override
    public void labelRevisionGraph() throws InterruptedException {
        // Todo to refactor
        List<SwhVulnerabilityRange> missingRanges = new LinkedList<>();

        AtomicInteger index = new AtomicInteger(0);
        try (ProgressBar pb = new ProgressBarBuilder()
                .setInitialMax(swhVulnerabilityRanges.size())
                .setTaskName("Labelling !")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {
            ExecutorService executorService = Executors.newWorkStealingPool(THREAD_NB);
            List<Future<Void>> futures = executorService.invokeAll(

                    this.swhVulnerabilityRanges.stream().map(range -> {
                        int id = index.getAndIncrement();
                        Callable<Void> task = () -> {
                            labelRangeThreaded(range, id, true);
                            pb.step();
                            return null;
                        };
                        return task;
                    }).collect(Collectors.toList()));
            for (var future : futures) {
                try {
                    future.get();

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    // To be rewrite, retrieve the memory intensive task exception
                    Throwable cause = e.getCause().getCause().getCause();
                    ;
                    if (cause instanceof MemoryIntensiveTaskException) {
                        missingRanges.add(((MemoryIntensiveTaskException) cause).range);
                    } else {
                        throw new RuntimeException(e);
                    }

                }
            }
        }

        logger.info("Saving main jobs --START--");
        try {
            export(Paths.get(resultTmpPath, "main_jobs").toString(), affectedRevisions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("Saving main jobs --OVER--");
        this.affectedRevisions = new AffectedRevisionsOpti(this.swhVulnerabilityRanges);

        logger.info("Computing missing Range - nb :" + missingRanges.size());
        try (ProgressBar pb = new ProgressBarBuilder()
                .setInitialMax(missingRanges.size())
                .setTaskName("Labelling !")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {
            ExecutorService executorService = Executors.newWorkStealingPool(50);
            List<Future<Void>> futures = executorService.invokeAll(

                    missingRanges.stream().map(range -> {
                        Callable<Void> task;

                        task = () -> {
                            labelRangeThreaded(range, this.affectedRevisions.getRangeId(range), false);
                            pb.step();
                            return null;
                        };

                        return task;
                    }).collect(Collectors.toList()));
            for (var future : futures) {
                try {
                    future.get();

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {

                    throw new RuntimeException(e);

                }
            }
        }
        logger.info("Saving big jobs --START--");
        try {
            export(Paths.get(resultTmpPath, "big_jobs").toString(), affectedRevisions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("Saving big jobs --OVER--");
        // Wipping to avoid modification of behaviour of temp save
        this.affectedRevisions = new AffectedRevisionsOpti(this.swhVulnerabilityRanges);

        loadAllTmp();

    }

    /**
     * Compute the affected revision of a given range after a visit.
     * Basically if the range is not a "limited" range ie a range that does not
     * contains a limit event,
     * all the Status.presumedVulnerable node are considered as vulnerable at the
     * end of the traversal.
     * 
     * However, if the range is a limited range, we will consider as vulnerable only
     * the range on the
     * linear path between the limit rev(s) and an introduction node. It means that
     * all the node
     * Exemple with fixed :
     * O-O-Introduced-X-X-X-Fixed-O-O-O
     * \X-X-X-X-X
     * Exemple with limit :
     * O-O-Introduced-X-X-X-Limit-O-O-O
     * \O-O-O-O-O
     * 
     * @param revStatus
     * @param limitRevision
     * @return
     */
    private static LongArrayList getAffectedRevsAfterVisit(Long2BooleanOpenHashMap revStatus,
            SwhVulnerabilityRange range,
            SwhBidirectionalGraph graphCopy) {
        LongArrayList vulnerableRevs = new LongArrayList();

        if (range.getRevisionByEvent(EventType.LIMIT).size() == 0) {
            // The presumed rev are now considered as vulnerable !
            revStatus.entrySet().stream()
                    .filter(e -> !e.getValue())
                    .forEach(r -> vulnerableRevs.add(r.getKey()));
        } else {
            boolean containsFixed = !range.getRevisionByEvent(EventType.FIXED).isEmpty();
            LongStack stack = new LongArrayList();
            LongOpenHashSet visited = new LongOpenHashSet();
            for (var event : range.getRevisionByEvent(EventType.LIMIT)) {
                stack.push(event);
                visited.add(event);
            }
            while (!stack.isEmpty()) {
                long currentRevId = stack.pop();
                Boolean isNonVulnerable = revStatus.getOrDefault(currentRevId, null);
                if (isNonVulnerable != null && !isNonVulnerable) {
                    vulnerableRevs.add(currentRevId);
                }
                /*
                 * If it contains fixed event and that the current node is not presumed
                 * vulnerable
                 * we should continue to handle such case :
                 * O-O-Introduced-X-X-X-Fixed-O-O-O-Limit
                 * \O-O-O-O-O
                 */
                if (isNonVulnerable != null && !isNonVulnerable
                        || containsFixed && range.getType(currentRevId) != EventType.INTRODUCED
                        || range.getType(currentRevId) == EventType.LIMIT) {
                    LazyLongIterator it = graphCopy.successors(currentRevId);
                    for (long neighborNodeId; (neighborNodeId = it.nextLong()) != -1;) {
                        if (graphCopy.getNodeType(neighborNodeId) == SwhType.REV) {
                            long parentRev = neighborNodeId;
                            if (!visited.contains(parentRev)) {
                                stack.push(parentRev);
                                visited.add(parentRev);
                            }
                        }
                    }

                }
            }
        }

        return vulnerableRevs;
    }

    public void labelRangeThreaded(SwhVulnerabilityRange range, int id, Boolean exceptionOnMemoryIntesiveTask)
            throws MemoryIntensiveTaskException {
        try {
            LongArrayList res = labelRange(range, this.getThreadSafeeGraph(), exceptionOnMemoryIntesiveTask);
            this.affectedRevisions.putAll(res, id);

        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadAllTmp() {
        Collection<File> files = FileUtils.listFiles(Paths.get(outdir, resultTmpPath).toFile(), TrueFileFilter.INSTANCE,
                TrueFileFilter.INSTANCE);
        if (files.size() > 0) {
            logger.info("Found files ");

            for (File file : ProgressBar.wrap(files, Progress.infoBarBuilder("Loading Temp File !", logger))) {
                AffectedRevisionsOpti current;
                try {
                    current = (AffectedRevisionsOpti) BinIO.loadObject(file);
                    this.affectedRevisions.putAll(current);

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    throw new RuntimeException(e);
                }
            }

        } else {
            logger.info("No files found");
        }

    }

    public LongArrayList labelRange(SwhVulnerabilityRange range, SwhBidirectionalGraph graphCopy,
            Boolean exceptionOnMemmoryIntesiveTask) throws MemoryIntensiveTaskException {
        // HashMap<Long, Status> revStatus = new HashMap<>();
        Long2BooleanOpenHashMap revisionToIsPatched = new Long2BooleanOpenHashMap();
        Long maxRevStatusSize = 30000000L;
        Long maxStack = 30000000L;
        Stack<RevStatus> stack = new Stack<>();
        int introducedNb = range.getRevisionByEvent(EventType.INTRODUCED).size();
        for (var introductionRev : range.getRevisionByEvent(EventType.INTRODUCED)) {
            stack.push(new RevStatus(introductionRev, false));
        }
        while (!stack.isEmpty()) {
            RevStatus currentRevision = stack.pop();

            if (exceptionOnMemmoryIntesiveTask
                    && (revisionToIsPatched.size() > maxRevStatusSize || stack.size() > maxStack)) {
                logger.warn("Will raise exception on following range, memory intesive");
                logger.warn("revStatus.size()=" + revisionToIsPatched.size() + " stack.size()=" + stack.size() + range);
                throw new MemoryIntensiveTaskException(range);
            }

            Boolean isPatched = revisionToIsPatched.getOrDefault(currentRevision.getId(), null);
            // Update the status if needed, ie if we are not in the following case :
            // If the visitedStatus is Status.nonVulnerable it means that a fix has been
            // encountered so the node cannot become vulnerable --> No need of update
            // If the visitedStatus is equals to the currentRevision.parentStatus the update
            // will have no effect --> No need of update
            // add introducednb>1 to handle the multi range case
            if (isPatched == null || (!isPatched && currentRevision.isParentNonVulnerable)) {
                if (range.getType(currentRevision.getId()) == EventType.INTRODUCED) {
                    isPatched = false;
                } else if (range.getType(currentRevision.getId()) == EventType.FIXED
                        || range.getType(currentRevision.getId()) == EventType.LIMIT) {
                    isPatched = true;
                } else {
                    isPatched = currentRevision.getIsParentNonVulnerable();
                }
                revisionToIsPatched.put(currentRevision.getId(), isPatched);
                // The status have been updated/created, propagate to child
                final LazyLongIterator childIt = graphCopy.predecessors(currentRevision.getId());
               
                for (long childId; (childId = childIt.nextLong()) != -1;) {
                    if (graphCopy.getNodeType(childId) == SwhType.REV) {
                        // If the currentRevision is a last_affected event then its child will be
                        // Status.nonVulnerable
                        if (range.getType(currentRevision.getId()) == EventType.LAST_AFFECTED) {
                            if (range.getType(childId) == EventType.INTRODUCED) {
                                // fix introduced-last_affected/Linear4.json
                                stack.push(new RevStatus(childId, false));

                            } else {
                                stack.push(new RevStatus(childId, true));
                            }
                        } else {
                            stack.push(new RevStatus(childId, isPatched));
                        }
                    }
                }
            }
        }

        return getAffectedRevsAfterVisit(revisionToIsPatched, range, graphCopy);
    }
 

    public static class RevStatus {
        private final Long id;
        private final boolean isParentNonVulnerable;

        public RevStatus(long id, boolean isParentNonVulnerable) {
            this.id = id;
            this.isParentNonVulnerable = isParentNonVulnerable;
        }

        public Long getId() {
            return id;
        }

        public boolean getIsParentNonVulnerable() {
            return isParentNonVulnerable;
        }

    }

    public void close() {
        this.affectedRevisions.close();
    }

    public static void main(final String[] args) throws IOException, InterruptedException, ClassNotFoundException {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String outdir = config.getString("outdir");
        final String vulnPreprocessRangesPath = config.getString("vulnPreprocessRangesPath");
        final OsvLabel tp = new OsvLabel(graphPath, vulnPreprocessRangesPath, outdir, true);
        // tp.labelRevisionGraphSequentially();
        tp.labelRevisionGraph();
        tp.export();
        tp.close();
    }

    /**
     * @return a map containing a correspondance between a swhid and its
     *         vulnerabilities detected
     */
    public Map<String, List<String>> getSwhIDToVulnerabilityIds() {
        Map<String, List<String>> outMap = new HashMap<>();
        Map<Long, List<SwhVulnerabilityRange>> map = this.affectedRevisions.getMap();
        map.entrySet().stream().forEach(entry -> {
            String swhId = this.graph.getSWHID(entry.getKey()).getSWHID();
            outMap.put(swhId, entry.getValue().stream().map(e -> {
                return e.getVulnerabilityId();
            }).collect(Collectors.toList()));
        });

        return outMap;
    }

    public String getSwhidFromId(long id) {
        return this.graph.getSWHID(id).getSWHID();
    }

    public void export(String out, AffectedRevisionsOpti affectedRevisions) throws IOException {
        ToolBox.createParentIfNeeded(Paths.get(outdir, out).toString());
        BinIO.storeObject(affectedRevisions, Paths.get(outdir, out).toString());
    }

    public void export() throws IOException {
        logger.info("Writting to " + resultPath + "--START--");
        export(resultPath, this.affectedRevisions);
        logger.info("Writting to " + resultPath + "--OVER--");

    }

    public class MemoryIntensiveTaskException extends Exception {
        MemoryIntensiveTaskException(SwhVulnerabilityRange range) {
            this.range = range;
        }

        public SwhVulnerabilityRange range;

    }

}
