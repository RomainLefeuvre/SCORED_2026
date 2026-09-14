package fr.inria.diverse.model;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.rocksdb.RocksDBException;
import org.softwareheritage.graph.SwhBidirectionalGraph;

import fr.inria.diverse.Utils.ToolBox;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.io.BinIO;

public class CommitToOriginHashMap implements CommitToOrigin {


    Logger logger = Logger.getLogger(CommitToOriginHashMap.class.getName());

    // list containing all the nodeIds of the origins to check
    List<Long> allOrigins;

    // Long is the commit, IntArrayList is the list id of the origins in the list
    // above
    // Map<Long, IntArrayList> GeneralCommitToOrigin;
    Map<Long, Integer> originNodeToIndexInAllOrigin;

    ThreadLocal<HashMap<Long, IntArrayList>> commitToOrigin = new ThreadLocal<>();

    /**
     * 
     * @param size the number of nodes in the graph
     */
    public CommitToOriginHashMap(List<Long> allOrigins) {
        this.allOrigins = allOrigins;
        int i = 0;
        originNodeToIndexInAllOrigin = new HashMap<>();

        for (Long origin : allOrigins) {
            originNodeToIndexInAllOrigin.put(origin, i++);
        }
    }

    public CommitToOriginHashMap() {
    }

    public void export(String path) throws IOException {
        logger.info(Thread.currentThread().getName() + " Exporting commitToOriginHashMap to " + getMap());
        BinIO.storeObject(commitToOrigin.get(), path);
    }

    public HashMap<Long, IntArrayList> getMap() throws IOException {
        if (commitToOrigin.get() == null) {
            commitToOrigin.set(new HashMap<>());

        }
        return commitToOrigin.get();
    }

    @Override
    @Deprecated
    /**
     * Not optimized for this implementation
     */
    public void put(long commit, long origin) throws RocksDBException, IOException {
        throw new UnsupportedOperationException("Unimplemented method 'put'");
    }

    @Override
    public void printAll(boolean multipleOrigins, int limit, SwhBidirectionalGraph graph) {

        return;
    }

    @Override
    public void putAll(List<Long> commit, long origin) throws RocksDBException, IOException {
        HashMap<Long, IntArrayList> commitToOriginThreadMap = getMap();
        for (var c : commit) {
            commitToOriginThreadMap.putIfAbsent(c, new IntArrayList());

            commitToOriginThreadMap.get(c).add((int) originNodeToIndexInAllOrigin.get(origin));

        }
        if (commitToOriginThreadMap.size() > 50_000_000) {
            logger.info("commitToOriginHashMap has more than 50000000 entries, exporting... "
                    + Thread.currentThread().getName() + "_" + System.currentTimeMillis() + ".bin");
            try {
                BinIO.storeObject(getMap(),
                        "/home/creux/workspace/swh_osv/java/fr.inria.diverse.swh_osv/out/originToVisit/"
                                + Thread.currentThread().getName() + "_" + System.currentTimeMillis() + ".bin");
                getMap().clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
