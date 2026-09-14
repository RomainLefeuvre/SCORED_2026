package fr.inria.diverse;

import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;

import com.google.common.primitives.Longs;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import me.tongfei.progressbar.ProgressBar;

public class CommitToOriginTopoTest {

    private final static String NAME = "rocks-db";
    private final static String PATH = "out/CtoOdb";
    final private static String GRAPH_PATH_STRING = "/graph/2021-03-23-popular-3k-python/compressed/graph";

    private static File dbDir;
    private static TransactionDB db;
    private static SwhBidirectionalGraph graph;

    private static ArrayList<Long> allOrigins;

    private static final Logger logger = LogManager.getLogger(CommitToOriginTopoTest.class.getName());


    public static void setUp() throws IOException {
        graph = SwhBidirectionalGraph.loadMapped(GRAPH_PATH_STRING);
        graph.loadMessages();
        allOrigins = new ArrayList<>();
        try (ProgressBar pb = new ProgressBar("getting origins !", graph.numNodes())) {

            for (long k = 0; k < graph.numNodes(); ++k) {
                pb.step();
                if (graph.getNodeType(k) == SwhType.ORI) {
                    allOrigins.add(k);
                }
            }
        }



        // Rocksdb Initialization
        RocksDB.loadLibrary();
        final Options options = new Options();
        final TransactionDBOptions transactionOptions = new TransactionDBOptions();
        options.setCreateIfMissing(true);
        dbDir = new File(PATH, NAME);

        try {
            Files.createDirectories(dbDir.getParentFile().toPath());
            Files.createDirectories(dbDir.getAbsoluteFile().toPath());
            db = TransactionDB.open(options, transactionOptions, dbDir.getAbsolutePath());
        } catch (IOException | RocksDBException ex) {
            logger.error(
                    "Error initializng RocksDB, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        logger.info("RocksDB initialized");

    }


    public void simpleDivergenceTest() throws RocksDBException, ClassNotFoundException, IOException {
        String commitToOriginPath = this.getClass().getResource("/CtoOdb/").getPath()
                .concat("referenceCommitToOrigin.bin");
        HashMap<Long, IntArrayList> commitToOriginReference = (HashMap<Long, IntArrayList>) BinIO.loadObject(commitToOriginPath);

        for (Map.Entry<Long, IntArrayList> entry : commitToOriginReference.entrySet()) {
            IntOpenHashSet originIndexInDatabase = SerializationUtils.deserialize(db.get(Longs.toByteArray(entry.getKey())));
            entry.getValue().forEach(originIndex -> {
                assertAll("url "+ graph.getUrl(allOrigins.get(originIndex)) + " not found in the database with correspondance to the commit " + entry.getKey()
                ,() -> {
                    assert (originIndexInDatabase.contains(originIndex));
                });
            });
        }
    }
}
