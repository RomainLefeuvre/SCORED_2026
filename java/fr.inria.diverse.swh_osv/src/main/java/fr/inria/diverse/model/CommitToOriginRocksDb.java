package fr.inria.diverse.model;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.TransactionOptions;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.softwareheritage.graph.SwhBidirectionalGraph;

import com.google.common.primitives.Longs;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public class CommitToOriginRocksDb implements CommitToOrigin {
    private final static String NAME = "rocks-db";
    private final static String PATH = "out/CtoOdb";

    private static final Logger logger = LogManager.getLogger(CommitToOriginRocksDb.class);
    private File dbDir;
    private TransactionDB db;

    public CommitToOriginRocksDb() {
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

 

    @Override
    public void put(long commit, long origin) throws RocksDBException {

        try (final TransactionOptions txnOptions = new TransactionOptions();
                final Transaction txn = db.beginTransaction(new WriteOptions(), txnOptions)) {
            byte[] retrievedValue = txn.getForUpdate(new ReadOptions(), Longs.toByteArray(commit), true);
            if (retrievedValue != null) {
                LongArrayList retrievedList = (LongArrayList) SerializationUtils.deserialize(retrievedValue);
                retrievedList.add(origin);
               /*  if(retrievedList.size() > 1 && origin == retrievedList.getLong(0)){
                    logger.info("Commit {} has more than one origin", commit);
                } */
                    
                txn.put(Longs.toByteArray(commit), SerializationUtils.serialize(retrievedList));

            } else {
                txn.put(Longs.toByteArray(commit), SerializationUtils.serialize(LongArrayList.of(origin)));
            }
            txn.commit();
        }

    }

    public void putAll(HashMap<Long, LongArrayList> map) throws RocksDBException {
/*         map.forEach((k,v)->{
            logger.info( k + " " + v);
        }); */
        map.forEach((k, v) -> {
            v.forEach(ori -> {
                try {
                    put(k, ori);
                } catch (RocksDBException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    public RocksIterator newIterator() {
        return db.newIterator();
    }



    @Override
    public void export(String path) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'export'");
    }



    @Override
    public void printAll(boolean multipleOrigins, int limit, SwhBidirectionalGraph graph) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'printAll'");
    }



    @Override
    public void putAll(List<Long> commit, long origin) throws RocksDBException, IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'putAll'");
    }

}
