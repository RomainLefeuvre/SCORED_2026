package fr.inria.diverse.model;

import java.util.List;
import java.util.Map;

/**
 * IAffectedRevision
 */
public interface IAffectedRevision {

    public Map<Long, List<SwhVulnerabilityRange>> getMap(); 

    public void addRangeToRevision(Long affectedRevisionId, SwhVulnerabilityRange range);

    public void addAllRangesToRevision(Long affectedRevisionId, List<SwhVulnerabilityRange> ranges);

    public void putAll(AffectedRevisions affectedRevisions);

    public List<SwhVulnerabilityRange> getRevisionRange(Long revision); 
    
    public void close();

}