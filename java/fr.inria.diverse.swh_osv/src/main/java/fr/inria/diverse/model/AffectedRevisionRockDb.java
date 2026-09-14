package fr.inria.diverse.model;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.TransactionOptions;
import org.rocksdb.WriteOptions;
import com.google.common.primitives.Longs;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;

public class AffectedRevisionRockDb implements IAffectedRevision {
    private final static String NAME = "rocks-db";
    private final static String PATH = "out/db";

    private static final Logger logger = LogManager.getLogger(AffectedRevisionRockDb.class);

    private File dbDir;
    private TransactionDB db;
    private List<SwhVulnerabilityRange> ranges;

    public AffectedRevisionRockDb(List<SwhVulnerabilityRange> ranges, String name) {
        this.ranges = ranges;
        RocksDB.loadLibrary();
        final Options options = new Options();
        final TransactionDBOptions transactionOptions = new TransactionDBOptions();
        options.setCreateIfMissing(true);
        dbDir = new File(PATH, NAME);

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
    }

   
    
        public AffectedRevisionRockDb(List<SwhVulnerabilityRange> ranges) {
          this(ranges,"rocks-db");
        }

    public void addRangeRevisionbyId(Long revisionId, int rangeId) throws RocksDBException {
        try (final TransactionOptions txnOptions = new TransactionOptions();
                final Transaction txn = db.beginTransaction(new WriteOptions(), txnOptions)) {
            ReadOptions readOption = new ReadOptions();
            byte[] keyBytes = Longs.toByteArray(revisionId);

            byte[] valueBytes = txn.getForUpdate(readOption, keyBytes, true);
            IntArrayList value = valueBytes == null ? new IntArrayList(1) : SerializationUtils.deserialize(valueBytes);
            value.add(rangeId);
            txn.put(keyBytes, SerializationUtils.serialize(value));
            txn.commit();
        }
    }

    public int getRangeId(SwhVulnerabilityRange range) {
        return ranges.indexOf(range);

    }

    public void addRangeToRevision(Long affectedRevisionId, SwhVulnerabilityRange range) {
        try {
            this.addRangeRevisionbyId(affectedRevisionId, getRangeId(range));
        } catch (RocksDBException e) {
            throw new RuntimeException("Error while inserting " + range, e);
        }
    }

    public void close() {
        this.db.close();
    }

    public IntArrayList getRevisionRangesId(Long revisionId) throws RocksDBException {
        byte[] keyBytes = Longs.toByteArray(revisionId);
        byte[] valuesBytes = db.get(keyBytes);
        return valuesBytes == null ? null : SerializationUtils.deserialize(valuesBytes);
    }

    public void putAll(LongArrayList revisionsId, int rangeId) throws RocksDBException {

        for (var revisionId : revisionsId) {
            this.addRangeRevisionbyId(revisionId, rangeId);
        }

    }

    @Override
    public Map<Long, List<SwhVulnerabilityRange>> getMap() {
        Map<Long, List<SwhVulnerabilityRange>> map = new HashMap<>();

        try (RocksIterator iterator = db.newIterator()) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                byte[] keyBytes = iterator.key();
                byte[] valuesBytes = iterator.value();
                IntArrayList value = SerializationUtils.deserialize(valuesBytes);
                List<SwhVulnerabilityRange> ranges = recomputeRangeListFromId(value);
                map.put(Longs.fromByteArray(keyBytes), ranges);
            }
        }
        return map;
    }

    private List<SwhVulnerabilityRange> recomputeRangeListFromId(IntArrayList rangesIds){
        List<SwhVulnerabilityRange> res = new ArrayList<>();
                for(var index : rangesIds){
                    res.add(this.ranges.get(index));
                }
            return res;
    }

    @Override
    public void addAllRangesToRevision(Long affectedRevisionId, List<SwhVulnerabilityRange> ranges) {
        for (var range : ranges) {
            this.addRangeToRevision(affectedRevisionId, range);
        }
    }

    @Override
    public void putAll(AffectedRevisions affectedRevisions) {
        for (var entry : affectedRevisions.getMap().entrySet()) {
            this.addAllRangesToRevision(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public List<SwhVulnerabilityRange> getRevisionRange(Long revision) {
       try {
        return this.recomputeRangeListFromId(this.getRevisionRangesId(revision));
    } catch (RocksDBException e) {
        // TODO Auto-generated catch block
        throw new RuntimeException("Error while getting rev "+revision,e);
    }
    }
}
