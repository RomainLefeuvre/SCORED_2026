package fr.inria.diverse.model;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

import fr.inria.diverse.Utils.Progress;
import fr.inria.diverse.Utils.ToolBox;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.tongfei.progressbar.ProgressBar;

public class VulnerableProvenanceMap implements Serializable, Iterable<Map.Entry<Long, List<String>>> {

    private String provenance_path;
    public LongArrayList originIndexes;
    private SwhBidirectionalGraph graph;
    static ThreadLocal<SwhBidirectionalGraph> threadGraph = new ThreadLocal<>();

    public List<IntOpenHashSet> allOriginIndexSets;
    ConcurrentHashMap<IntOpenHashSet, Integer> originIndexSetToIndexInAllOriginSetMap;
    public ConcurrentHashMap<Long, Integer> commitToOriginIndexSetMap;
    AtomicInteger currentIndex;

    final private static Logger logger = LogManager.getLogger(VulnerableProvenanceMap.class.getName());

    public SwhBidirectionalGraph getThreadSafeeGraph() {
        if (threadGraph.get() == null) {
            threadGraph.set(graph.copy());
            logger.info("Copying Graph");
        }
        return threadGraph.get();
    }

    public void keepOriginId(LongOpenHashSet originIdsToKeep) {
        // Get the internal id of range origin, that will be keeped
        IntOpenHashSet originInternalIdToKeep = ProgressBar
                .wrap(originIdsToKeep.longStream().parallel(),
                        Progress.infoBarBuilder("Getting internal id", logger))
                .map(originIndexes::indexOf)
                .mapToInt(x -> (int) x)
                .collect(IntOpenHashSet::new, IntOpenHashSet::add, IntOpenHashSet::addAll);

        try (ProgressBar pb = Progress.infoBar("filtering provenance", logger,
                allOriginIndexSets.size())) {

            allOriginIndexSets.parallelStream()
                    .forEach(set -> {
                        if (set.size() == 0) {
                            set.add(-1);
                        } else {
                            set.removeIf(o -> !originInternalIdToKeep.contains(o));
                        }
                        pb.step();
                    });
        }
    }

    /**
     * Warning : read-Only in concurrent access
     * 
     * @return
     */
    public Stream<Long> revisionParallelStream() {
        return commitToOriginIndexSetMap.keySet().parallelStream();
    }

    public boolean containsKey(long key) {
        return commitToOriginIndexSetMap.containsKey(key);
    }

    public void exportAsJson(String path) throws IOException {
        File jsonOutputFile = new File(path);
        Gson gson = new Gson();
        try (ProgressBar pb = new ProgressBar("Storing as json",
                commitToOriginIndexSetMap.size())) {
            PrintWriter pw = new PrintWriter(jsonOutputFile);
            pw.println("{");
            Iterator<Long> it = this.commitToOriginIndexSetMap.keySet()
                    .iterator();
            while (it.hasNext()) {
                pb.step();
                Long revisionNodeId = it.next();

                String key = graph.getSWHID(revisionNodeId).toString();

                JsonArray urlArray = new JsonArray();

                getUrls(revisionNodeId).forEach(urlArray::add);

                pw.print("\"" + key + "\":");
                pw.print(gson.toJson(urlArray));
                if (it.hasNext()) {
                    pw.println(",");
                } else {
                    pw.println();
                }
            }
            pw.println("}");
            pw.close();
        }

    }

    /**
     * Exports the map as a single binary file containing all the data in a hashmap
     * 
     * @param path
     * @throws IOException
     */
    public void exportAsFullMap(String path) throws IOException {

        ConcurrentHashMap<Long, LongOpenHashSet> fullMap = new ConcurrentHashMap<>();

        try (ProgressBar pb = new ProgressBar("Storing as  binary",
                commitToOriginIndexSetMap.size())) {

            commitToOriginIndexSetMap.keySet().parallelStream().forEach(revision -> {
                pb.step();
                fullMap.put(revision, getOriginNodeIds(revision));
            });
        }
        logger.info("storing full map");
        BinIO.storeObject(fullMap, path);
    }

    private void VulnerableProvenanceMapConstruct(String graphPath) throws IOException, ClassNotFoundException {
        currentIndex = new AtomicInteger(0);
        commitToOriginIndexSetMap = new ConcurrentHashMap<>();
        allOriginIndexSets = new ArrayList<>();
        originIndexSetToIndexInAllOriginSetMap = new ConcurrentHashMap<>();
        graph = SwhBidirectionalGraph.loadMapped(graphPath);
        graph.loadMessages();
        ToolBox.createParentIfNeeded(provenance_path + "/OriginIndexes.bin");

        File f = new File(provenance_path + "OriginIndexes.bin");
        if (f.exists() && !f.isDirectory()) {
            logger.info("temp file exists, initializing originIndexex from it");
            originIndexes = (LongArrayList) BinIO.loadObject(provenance_path + "OriginIndexes.bin");
        } else {
            logger.info("No cache found for origin index list, recomputing");
            try (ProgressBar pb = new ProgressBar("getting origins indexes", graph.numNodes())) {
                originIndexes = new LongArrayList();
                for (long k = 0; k < graph.numNodes(); ++k) {
                    pb.step();
                    if (graph.getNodeType(k) == SwhType.ORI) {
                        originIndexes.add(k);
                    }
                }
            }
            logger.info("saving origin indexes");
            BinIO.storeObject(originIndexes, provenance_path + "OriginIndexes.bin");
        }
    }

    public void forEachIntOpenHashSet(BiConsumer<Long, IntOpenHashSet> action) {
        Long k;
        IntOpenHashSet v;
        for (Iterator<Entry<Long, Integer>> mapIterator = this.commitToOriginIndexSetMap.entrySet()
                .iterator(); mapIterator.hasNext(); action.accept(k, v)) {
            Entry<Long, Integer> entry = (Entry<Long, Integer>) mapIterator.next();

            try {
                k = entry.getKey();
                v = get(k);
            } catch (IllegalStateException e) {
                throw new ConcurrentModificationException(e);
            }
        }
    }

    public void forEach(BiConsumer<Long, List<String>> action) {
        Long k;
        List<String> v;
        for (Iterator<Entry<Long, Integer>> mapIterator = this.commitToOriginIndexSetMap.entrySet()
                .iterator(); mapIterator.hasNext(); action.accept(k, v)) {
            Entry<Long, Integer> entry = (Entry<Long, Integer>) mapIterator.next();

            try {
                k = entry.getKey();
                v = getUrls(k);
                for (String url : v) {
                    if (v == null || v.equals("")) {
                        logger.error("error for" + k + " no url in the graph" + " swhid is " + this.graph.getSWHID(k));
                    }
                }
            } catch (IllegalStateException e) {
                throw new ConcurrentModificationException(e);
            }
        }
    }

    /**
     * creates or imports a vulnerableProvenanceMap
     * 
     * @param graphPath    the path to the graph
     * @param existingPath the path to the folder containing the vulnerable
     *                     provenance map
     * @param importOnly   whether to use the constructor only for importing and not
     *                     computing
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unchecked")
    public VulnerableProvenanceMap(String graphPath, String existingPath, Boolean importOnly)
            throws IOException, ClassNotFoundException {
        // import all the data from the path
        this.provenance_path = existingPath;
        if (!importOnly) {
            VulnerableProvenanceMapConstruct(graphPath);
            return;
        }
        graph = SwhBidirectionalGraph.loadMapped(graphPath);
        graph.loadMessages();
        allOriginIndexSets = (List<IntOpenHashSet>) BinIO.loadObject(existingPath + "allOriginIndexSets.bin");
        originIndexSetToIndexInAllOriginSetMap = (ConcurrentHashMap<IntOpenHashSet, Integer>) BinIO
                .loadObject(existingPath + "originIndexSetToIndexInAllOriginSetMap.bin");
        commitToOriginIndexSetMap = (ConcurrentHashMap<Long, Integer>) BinIO
                .loadObject(existingPath + "commitToOriginIndexSetMap.bin");
        originIndexes = (LongArrayList) BinIO.loadObject(existingPath + "OriginIndexes.bin");
    }

    public void put(Long commit, IntOpenHashSet originIndexSet) {
        if (!originIndexSetToIndexInAllOriginSetMap.containsKey(originIndexSet)) {
            allOriginIndexSets.add(originIndexSet);
            commitToOriginIndexSetMap.put(commit, currentIndex.get());
            originIndexSetToIndexInAllOriginSetMap.put(originIndexSet, currentIndex.getAndIncrement());
        } else {
            Integer index = originIndexSetToIndexInAllOriginSetMap.get(originIndexSet);
            commitToOriginIndexSetMap.put(commit, index);
        }
    }

    public Integer getIndex(Long commit) {
        return commitToOriginIndexSetMap.get(commit);

    }

    public IntOpenHashSet get(Long commit) {
        Integer index = commitToOriginIndexSetMap.get(commit);
        if (index == null) {
            return null;
        }
        return allOriginIndexSets.get(index.intValue());
    }

    public LongOpenHashSet getOriginNodeIds(Long commit) {
        IntOpenHashSet originIndexSet = get(commit);
        if (originIndexSet == null || originIndexSet.contains(-1)) {
            return null;
        }
        return originIndexSet.intStream().mapToObj(originIndexes::getLong).collect(LongOpenHashSet::new,
                LongOpenHashSet::add, LongOpenHashSet::addAll);
    }

    public List<String> getUrls(Long commit) {
        LongOpenHashSet originSet = getOriginNodeIds(commit);
        if (originSet == null) {
            return null;
        }
        return originSet.longStream().mapToObj(getThreadSafeeGraph()::getUrl).collect(Collectors.toList());
    }

    /**
     * Stores all the data in the path
     * 
     * @throws IOException
     */
    public void saveAll() throws IOException {
        BinIO.storeObject(allOriginIndexSets, this.provenance_path + "allOriginIndexSets.bin");
        BinIO.storeObject(originIndexSetToIndexInAllOriginSetMap,
                this.provenance_path + "originIndexSetToIndexInAllOriginSetMap.bin");
        BinIO.storeObject(commitToOriginIndexSetMap, this.provenance_path + "commitToOriginIndexSetMap.bin");
        BinIO.storeObject(originIndexes, this.provenance_path + "OriginIndexes.bin");
    }

    /**
     * Warning: this iterator is read only
     * i.e. it does not support remove, and the map should not be modified while
     * iterating
     * 
     * @return
     */
    @Override
    public Iterator<Entry<Long, List<String>>> iterator() {
        return new UrlIterator();
    }

    class UrlIterator implements Iterator<Entry<Long, List<String>>> {

        private Iterator<Entry<Long, Integer>> L = commitToOriginIndexSetMap.entrySet().iterator();

        public boolean hasNext() {
            return L.hasNext();
        }

        @Override
        public Entry<Long, List<String>> next() {
            return new Map.Entry<Long, List<String>>() {
                Entry<Long, Integer> entry = L.next();

                @Override
                public Long getKey() {
                    return entry.getKey();
                }

                @Override
                public List<String> getValue() {
                    return getUrls(entry.getKey());
                }

                @Override
                public List<String> setValue(List<String> value) {
                    return null;
                }
            };
        }
    }

    public long size() {
        return commitToOriginIndexSetMap.size();
    }

    public void filterFromAffectedRevision(AffectedRevisionsOpti affectedRevisionsOpti) {
        this.commitToOriginIndexSetMap = this.commitToOriginIndexSetMap.entrySet().stream().filter(entry -> {
            return affectedRevisionsOpti.getAllRevisions().contains(entry.getKey());
        }).collect(Collectors.toConcurrentMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (existing, replacement) -> existing, // in case of key conflicts
                ConcurrentHashMap::new // explicitly use ConcurrentHashMap as the target map type
        ));

    }

}
