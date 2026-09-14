package fr.inria.diverse.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.rocksdb.RocksDBException;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Optimized Map(RAM space) that maps a revision node ID to a list of ranges that affects it
 */
public class AffectedRevisionsOpti implements Serializable, IAffectedRevision {

    private static final long serialVersionUID = 6726618485642616182L;


    private List<SwhVulnerabilityRange> ranges;

    private Map<Long, IntArrayList> map;

  
    public Map<Long, List<SwhVulnerabilityRange>> getMap() {
        Map<Long, List<SwhVulnerabilityRange>> res = new HashMap<>();
        for (var entry : map.entrySet()) {
            res.put(entry.getKey(), this.recomputeRangeListFromId(entry.getValue()));
        }
        return res;
    }

    public AffectedRevisionsOpti(List<SwhVulnerabilityRange> ranges) {
        this.ranges = ranges;
        this.map = new ConcurrentHashMap<>();
    }

    public void addRangeToRevision(Long affectedRevisionId, SwhVulnerabilityRange range) {
        this.addRangeRevisionbyId(affectedRevisionId, getRangeId(range));

    }

    @Override
    public void addAllRangesToRevision(Long affectedRevisionId, List<SwhVulnerabilityRange> ranges) {
        for (var range : ranges) {
            this.addRangeToRevision(affectedRevisionId, range);
        }
    }

    public void putAll(LongArrayList revisionsId, int rangeId) throws RocksDBException {

        for (var revisionId : revisionsId) {
            this.addRangeRevisionbyId(revisionId, rangeId);
        }

    }

    @Override
    public void putAll(AffectedRevisions affectedRevisions) {
        for (var entry : affectedRevisions.getMap().entrySet()) {
            this.addAllRangesToRevision(entry.getKey(), entry.getValue());
        }
    }

    
    public void putAll(AffectedRevisionsOpti affectedRevisions) {
        assert(affectedRevisions.ranges.equals(this.ranges));
        for( Map.Entry<Long,IntArrayList> entry : affectedRevisions.map.entrySet()){
            if (this.map.containsKey(entry.getKey())){
                this.map.get(entry.getKey()).addAll(entry.getValue());
            }
            else{
                this.map.put(entry.getKey(),entry.getValue());
            }
        }       
    }

    private List<SwhVulnerabilityRange> recomputeRangeListFromId(IntArrayList rangesIds) {
        List<SwhVulnerabilityRange> res = new ArrayList<>();
        for (var index : rangesIds) {
            res.add(this.ranges.get(index));
        }
        return res;
    }

    @Override
    public List<SwhVulnerabilityRange> getRevisionRange(Long revision) {

            return this.recomputeRangeListFromId(this.getRevisionRangesId(revision));

    }

    public void close() {

    }

    public int getRangeId(SwhVulnerabilityRange range) {
        return ranges.indexOf(range);

    }

    public void addRangeRevisionbyId(Long revisionId, int rangeId) {
        IntArrayList list = map.computeIfAbsent(revisionId, key -> new IntArrayList());
        synchronized (list) {
            list.add(rangeId);
        }

    }

    public IntArrayList getRevisionRangesId(Long revisionId) {
        return this.map.get(revisionId);
    }

    public Set<Long> getAllRevisions() {
        return this.map.keySet();
    }

    public List<SwhVulnerabilityRange> getRanges() {
        return ranges;
    }

    
}
