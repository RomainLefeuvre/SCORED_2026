package fr.inria.diverse.model;

import java.io.IOException;
import java.util.List;

import org.rocksdb.RocksDBException;
import org.softwareheritage.graph.SwhBidirectionalGraph;

public interface CommitToOrigin {

    void put(long commit, long origin) throws RocksDBException,IOException;
    void putAll(List<Long> commit, long origin) throws RocksDBException,IOException;
    void export(String path) throws IOException;
    void printAll(boolean multipleOrigins, int limit, SwhBidirectionalGraph graph);
}