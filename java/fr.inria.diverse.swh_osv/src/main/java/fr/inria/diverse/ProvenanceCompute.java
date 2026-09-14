package fr.inria.diverse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Set;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import com.google.common.primitives.Longs;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import fr.inria.diverse.model.AffectedRevisionsOpti;
import fr.inria.diverse.model.VulnerableProvenanceMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

public class ProvenanceCompute {

    private String osvLabelResultPath;
    private File dbDir;
    private TransactionDB db;
    private VulnerableProvenanceMap provenanceMap;

    private LongArrayList originIndexes;

    final private static Logger logger = LogManager.getLogger(ProvenanceCompute.class.getName());

    /**
     * 
     * @param osvLabelResultPath the path to the computed affectedRevisionOpti
     *                           object
     * @throws IOException
     * @throws RocksDBException
     * @throws ClassNotFoundException
     */
    public ProvenanceCompute(String graphPath, String osvLabelResultPath, String rocksDbPath,
            String vulnerableProvenancePath)
            throws IOException, RocksDBException, ClassNotFoundException {
        this.provenanceMap = new VulnerableProvenanceMap(graphPath, vulnerableProvenancePath, false);
        this.osvLabelResultPath = osvLabelResultPath;
        logger.info("Loading Rocks DB --START--");
        // Rocksdb Initialization
        RocksDB.loadLibrary();
        logger.info("Loading Rocks DB --OVER--");

        final Options options = new Options();
        final TransactionDBOptions transactionOptions = new TransactionDBOptions();
        options.setCreateIfMissing(true);
        dbDir = new File(rocksDbPath);

        try {
            Files.createDirectories(dbDir.getParentFile().toPath());
            Files.createDirectories(dbDir.getAbsoluteFile().toPath());
            // RocksDB.destroyDB(dbDir.getAbsolutePath(), options);
            db = TransactionDB.open(options, transactionOptions, dbDir.getAbsolutePath());
        } catch (IOException | RocksDBException ex) {
            logger.error(
                    "Error initializng RocksDB, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        logger.info("RocksDB initialized");

    }

    /**
     * 
     * Computes a map for the vulnerabilitymap
     * 
     * @param recompute the map will be recomputed if true
     * @return the map containing only vulnerable revisions
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unchecked")
    public void ComputeVulnerableRevisionProvenanceMap()
            throws ClassNotFoundException, IOException {

        Set<Long> affectedRevisions = ((AffectedRevisionsOpti) BinIO.loadObject(osvLabelResultPath)).getAllRevisions();
        try (ProgressBar pb = new ProgressBarBuilder()
                .setInitialMax(affectedRevisions.size())
                .setTaskName("Computing the new hashMap with vulnerableCommits only")
                .setConsumer(new DelegatingProgressBarConsumer(logger::info))
                .build()) {
            affectedRevisions.stream().forEach(rev -> {
                pb.step();
                try {
                    byte[] byteOriginIndexForRev = db.get(Longs.toByteArray(rev));
                    if (byteOriginIndexForRev == null) {
                        logger.warn("no origin found for commit " + rev);

                    } else {
                        IntOpenHashSet originIndexForRev = SerializationUtils.deserialize(byteOriginIndexForRev);
                        provenanceMap.put(rev, originIndexForRev);
                    }
                } catch (RocksDBException e) {
                    e.printStackTrace();
                }
            });
        }
        provenanceMap.saveAll();
    }

    public HashMap<Long, IntOpenHashSet> getSpecialRevisionProvenanceMap(Set<Long> allRevision)
            throws ClassNotFoundException, IOException {
        HashMap<Long, IntOpenHashSet> revToOrigin = new HashMap<>();
        try (ProgressBar pb = new ProgressBar("Computing the new hashMap with vulnerableCommits only",
                allRevision.size())) {
            allRevision.stream().forEach(rev -> {
                pb.step();
                try {
                    IntOpenHashSet originIndexForRev = SerializationUtils.deserialize(db.get(Longs.toByteArray(rev)));
                    revToOrigin.put(rev, originIndexForRev);
                } catch (RocksDBException e) {
                    e.printStackTrace();
                }
            });
        }
        return revToOrigin;
    }

    /**
     * Gets the provenance of a revision Using Directly the rocksDB database
     * 
     * @param nodeId the id of the revision to get the origins from
     * @return the list of nodeIds of the origins
     * @throws RocksDBException
     */
    public LongOpenHashSet getProvenanceOfRev(long nodeId) throws RocksDBException {
        IntOpenHashSet originIndexForRev = SerializationUtils.deserialize(db.get(Longs.toByteArray(nodeId)));
        return originIndexForRev.intStream().mapToLong(index -> originIndexes.getLong(index))

                .collect(LongOpenHashSet::new, LongOpenHashSet::add, LongOpenHashSet::addAll);
    }

    public LongOpenHashSet originNodeIdsFromIndexes(IntOpenHashSet inpuSet) {
        return inpuSet.intStream().mapToLong(index -> originIndexes.getLong(index))
                .collect(LongOpenHashSet::new, LongOpenHashSet::add, LongOpenHashSet::addAll);
    }

    public static void main(String[] args) throws ClassNotFoundException, IOException, RocksDBException {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String rocksDBPath = config.getString("rocksDBPath");
        final String osvLabelResult = config.getString("osvLabelResult");
        final String vulnerableProvenancePath = config.getString("vulnerableProvenancePath");

        ProvenanceCompute provenanceCompute = new ProvenanceCompute(graphPath, osvLabelResult, rocksDBPath,
                vulnerableProvenancePath);

        provenanceCompute.ComputeVulnerableRevisionProvenanceMap();
    }

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(ProvenanceCompute.class.getName(), "",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("rocksDBPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'd',
                                    "rocksDBPath", "Path where the rocksDB database is stored"),
                            new FlaggedOption("osvLabelResult", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'a',
                                    "osvLabelResult", "Path to the binary of the affectedRevisionOpti"),
                            new FlaggedOption("vulnerableProvenancePath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'v',
                                    "vulnerableProvenancePath",
                                    "Path to the folder where the vulnerableProvenance Map will be stored")
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
