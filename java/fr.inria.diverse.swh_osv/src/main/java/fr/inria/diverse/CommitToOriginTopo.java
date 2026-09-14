package fr.inria.diverse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;

import com.google.common.primitives.Longs;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.model.CommitToOriginHashMap;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

/**
 * Topologic computation of the commitToOrigin map
 */
public class CommitToOriginTopo {
    final private static Logger logger = LogManager.getLogger(CommitToOriginHashMap.class.getName());
    private File dbDir;
    private TransactionDB db;
    SwhBidirectionalGraph graph;
    // Data structures to store a Map<Long, List<Long>>,
    // Speed up Long --> integer fetch , array of long not needed in this
    // computation
    HashMap<Long, Integer> originNodeIdToIndex;
    HashMap<Long, IntOpenHashSet> commitToOrigin;

    @SuppressWarnings("unchecked")
    public CommitToOriginTopo(String graphPath, String rocksDbPath, String backupPath)
            throws IOException, RocksDBException, ClassNotFoundException {
        graph = SwhBidirectionalGraph.loadMapped(graphPath);

        RocksDB.loadLibrary();
        final Options options = new Options();
        final TransactionDBOptions transactionOptions = new TransactionDBOptions();
        options.setCreateIfMissing(true);
        dbDir = new File(rocksDbPath);

        try {
            Files.createDirectories(dbDir.getParentFile().toPath());
            Files.createDirectories(dbDir.getAbsoluteFile().toPath());
            RocksDB.destroyDB(dbDir.getAbsolutePath(), options);
            db = TransactionDB.open(options, transactionOptions, dbDir.getAbsolutePath());
        } catch (IOException | RocksDBException ex) {
            logger.error(
                    "Error initializng RocksDB, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        logger.info("RocksDB init");

        try (ProgressBar pb = new ProgressBarBuilder()
                .setInitialMax(graph.numNodes())
                .setTaskName("getting origins of the originNodeIdToIndex map")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {
            originNodeIdToIndex = new HashMap<>();
            int i = 0;
            for (long k = 0; k < graph.numNodes(); ++k) {
                pb.step();
                if (graph.getNodeType(k) == SwhType.ORI) {
                    originNodeIdToIndex.put(k, i);
                    i++;
                }
            }
        }

    }

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(CommitToOriginTopo.class.getName(), "",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("rocksDBPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'd',
                                    "rocksDBPath", "Path where the rocksDB database will be stored"),
                            new FlaggedOption("topoSortedNodeList", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED,
                                    't',
                                    "topoSortedNodeList", "Path to the csv containing the topoSorted node list"),
                            new FlaggedOption("backupPath", JSAP.STRING_PARSER, "", JSAP.REQUIRED, 'b',
                                    "backupPath",
                                    "path to the backup, if files exists, they will be used to compute from the latest state,")
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

    public static void main(String[] args) {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String rocksDBPath = config.getString("rocksDBPath");
        final String topoSortedNodeList = config.getString("topoSortedNodeList");
        final String backupPath = config.getString("backupPath");

        try {

            CommitToOriginTopo commitToOriginTopo = new CommitToOriginTopo(graphPath, rocksDBPath, backupPath);

            commitToOriginTopo.computeTopo(topoSortedNodeList,
                    3_000_000_000L);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private IntOpenHashSet get(long revId) throws RocksDBException {
        byte[] tmp = db.get(Longs.toByteArray(revId));
        if (tmp == null)
            return null;
        return SerializationUtils.deserialize(tmp);
    }

    /**
     * Compute the topologic commit to origin map
     * 
     * @param backupSize               the size of the backup
     * @param topoSortedCommitsNodeIds the topological list of commits to consider
     * @param graph                    the graph
     * @param deleteIfNotNeeded
     * @return the commit to origin map
     * @throws IOException
     * @throws NumberFormatException
     * @throws ClassNotFoundException
     * @throws RocksDBException
     */
    @SuppressWarnings("unchecked")
    public void computeTopo(String topoSortedCommitsNodeIdsPath, Long backupSize)
            throws NumberFormatException, IOException, ClassNotFoundException, RocksDBException {

        commitToOrigin = new HashMap<>();

        try (ProgressBar pb = new ProgressBarBuilder()
                .setInitialMax(graph.numNodes())
                .setTaskName("tagging first commits of origins !")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {

            for (long k = 0; k < graph.numNodes(); ++k) {
                pb.step();
                if (graph.getNodeType(k) == SwhType.ORI) {
                    tagFirstCommitsOfOrigin(commitToOrigin, graph, k);
                }
            }
        }
        logger.info("first commit to origin map computed");

        String path = this.graph.getPath() + ".nodes.stats.txt";
        Path test = Paths.get(path);
        String[] graphStats = Files.readString(test).split("\n");

        Long numberOfRevs = Arrays.stream(graphStats)
                .filter(s -> s.contains("rev"))
                .map(line -> line.split(" ")[1])
                .mapToLong(Long::parseLong)
                .findFirst()
                .orElse(0L);
        try (ProgressBar pb = new ProgressBarBuilder()
                .setInitialMax(numberOfRevs)
                .setTaskName("tagging all commits")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {

            BufferedReader reader = new BufferedReader(new FileReader(topoSortedCommitsNodeIdsPath));
            String line;
            Long i = getCommitToOriginFullSetSize();
            Long commitNodeId = -1L;
            while ((line = reader.readLine()) != null) {
                try {

                    commitNodeId = Long.parseLong(line);
                } catch (Exception e) {
                    logger.warn("failed to parse line :" + line);
                    continue;
                }
                if (graph.getNodeType(commitNodeId) != SwhType.REV) {
                    continue;
                }
                pb.step();

                LazyLongIterator commitParents = graph.predecessors(commitNodeId);
                // getting the first commit if exists(i.e. the commit directly linked to an
                // origin)
                IntOpenHashSet originSet = new IntOpenHashSet();
                for (long ParentNodeId; (ParentNodeId = commitParents.nextLong()) != -1;) {
                    if (graph.getNodeType(ParentNodeId) == SwhType.REV) {
                        IntOpenHashSet parentOrigins = commitToOrigin.get(ParentNodeId);
                        if (parentOrigins != null) {
                            originSet.addAll(parentOrigins);
                        }

                    }
                }

                if (commitToOrigin.containsKey(commitNodeId)) {
                    commitToOrigin.get(commitNodeId).addAll(originSet);
                    // logger.warn("existing node:" + commitNodeId);
                } else {
                    commitToOrigin.put(commitNodeId, originSet);
                }
                i += (long) originSet.size() + 2L;// size of the number of ints plus a long for the key
                if ((i % 500_000) < 2) {
                    logger.info("i at " + i + " out of " + 6_000_000_000L);
                }
                if (i > 6_000_000_000L) {
                    RocksDBBackup(commitToOrigin, graph);
                    i = getCommitToOriginFullSetSize();
                }
            }

            reader.close();
            forceBackup();
            // GraphUtils.debugGraphNode(34000436l, graph, db, 3500, false);
        }

    }

    private long getCommitToOriginFullSetSize() {
        return (long) commitToOrigin.size() * 2L
                + commitToOrigin.values().stream().mapToLong(value -> value.size()).sum();
    }

    public void forceBackup() throws RocksDBException {
        WriteBatch writeBatch = new WriteBatch();
        WriteOptions option = new WriteOptions();
        Long i = 0L;

        try (ProgressBar pb2 = new ProgressBarBuilder()
                .setInitialMax(commitToOrigin.size())
                .setTaskName("Forcing backup of left nodes")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {
            logger.info("size of lefts nodes to backup " + commitToOrigin.size());

            for (Long id : this.commitToOrigin.keySet()) {
                pb2.step();
                try {
                    writeBatch.put(Longs.toByteArray(id), SerializationUtils.serialize(commitToOrigin.get(id)));
                    i++;
                    if (i % 1_000_000 == 0) {
                        db.write(option, writeBatch);
                        writeBatch.clear();
                    }
                } catch (RocksDBException e) {
                    e.printStackTrace();
                }
            }
            db.write(option, writeBatch);
        }
        writeBatch.close();
        logger.info("Final backup finished");
    }

    private void RocksDBBackup(HashMap<Long, IntOpenHashSet> commitToOrigin, SwhBidirectionalGraph graph2)
            throws RocksDBException {
        logger.info("size of commitToOrigin before backup " + commitToOrigin.size());

        List<Long> idsBackupable = new ArrayList<>();

        try (ProgressBar pb2 = new ProgressBarBuilder()
                .setInitialMax(commitToOrigin.size())
                .setTaskName("Checking for backup possibility")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {

            commitToOrigin.forEach((k, v) -> {
                pb2.step();
                boolean canRemoveAndSave = true;
                LazyLongIterator commitSucessors = graph.successors(k);
                for (long currentSucessor; (currentSucessor = commitSucessors.nextLong()) != -1;) {
                    if (graph.getNodeType(currentSucessor) == SwhType.REV) {
                        if (!commitToOrigin.containsKey(currentSucessor)
                                || !commitToOrigin.get(currentSucessor).containsAll(commitToOrigin.get(k))) {
                            canRemoveAndSave = false;
                            break;
                        }
                    }
                }
                if (canRemoveAndSave) {
                    idsBackupable.add(k);
                }
            });
        }
        logger.info(idsBackupable.size() + "nodeIds are backupable");
        WriteBatch writeBatch = new WriteBatch();
        WriteOptions option = new WriteOptions();
        try (ProgressBar pb2 = new ProgressBarBuilder()
                .setInitialMax(idsBackupable.size())
                .setTaskName("backing up in rocksDB")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {
            int i = 0;
            for (Long id : idsBackupable) {
                i++;
                try {
                    writeBatch.put(Longs.toByteArray(id), SerializationUtils.serialize(commitToOrigin.get(id)));
                    if (i % 1_000_000 == 0) {
                        db.write(option, writeBatch);
                        pb2.stepBy(1_000_000);
                        writeBatch.clear();
                    }
                } catch (RocksDBException e) {
                    e.printStackTrace();
                }
                commitToOrigin.remove(id);
            }
            pb2.stepBy(writeBatch.count());
        }
        db.write(option, writeBatch);
        writeBatch.clear();
        // logger.info(db.getProperty("rocksdb.stats"));
        logger.info("size of commitToOrigin after backup " + commitToOrigin.size());
    }

    /**
     * Tag the first commits of an origin
     * 
     * @param commitToOrigin the map to fill
     * @param graph          the graph
     * @param originId       the root origin to use for tagging
     * @throws RocksDBException
     */
    public void tagFirstCommitsOfOrigin(HashMap<Long, IntOpenHashSet> commitToOriginInit, SwhBidirectionalGraph graph,
            long originId) throws RocksDBException {
        LazyLongIterator originSuccesors = graph.successors(originId);

        for (long snapshotNodeId; (snapshotNodeId = originSuccesors.nextLong()) != -1;) {

            LazyLongIterator snapshotSuccesors = graph.successors(snapshotNodeId);

            for (long revisionOrReleaseNodeId; (revisionOrReleaseNodeId = snapshotSuccesors.nextLong()) != -1;) {

                if (graph.getNodeType(revisionOrReleaseNodeId) == SwhType.REV) {
                    IntOpenHashSet originSet = commitToOriginInit.getOrDefault(revisionOrReleaseNodeId,
                            new IntOpenHashSet());
                    if (originSet.isEmpty()) {
                        commitToOriginInit.put(revisionOrReleaseNodeId, originSet);
                    }
                    originSet.add((int) this.originNodeIdToIndex.get(originId));
                    // commitToOriginInit.put(revisionOrReleaseNodeId, originSet);

                } else if (graph.getNodeType(revisionOrReleaseNodeId) == SwhType.REL) {
                    LazyLongIterator releaseSuccesors = graph.successors(revisionOrReleaseNodeId);

                    for (long revisionRelNodeId; (revisionRelNodeId = releaseSuccesors.nextLong()) != -1;) {
                        IntOpenHashSet originSet = commitToOriginInit.getOrDefault(revisionRelNodeId,
                                new IntOpenHashSet());
                        if (originSet.isEmpty()) {
                            commitToOriginInit.put(revisionRelNodeId, originSet);
                        }
                        originSet.add((int) this.originNodeIdToIndex.get(originId));
                        // commitToOriginInit.put(revisionOrReleaseNodeId, originSet);
                    }
                }
            }
        }
    }

}
