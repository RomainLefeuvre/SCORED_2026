package fr.inria.diverse.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AffectedRevisions implements Serializable, IAffectedRevision{
        private Map<Long, List<SwhVulnerabilityRange> > map;

        public Map<Long, List<SwhVulnerabilityRange>> getMap() {
            return map;
        }

        public AffectedRevisions() {
            this.map=new HashMap<>();
        }
        
        public void addRangeToRevision(Long affectedRevisionId, SwhVulnerabilityRange range){
             this.map.putIfAbsent(affectedRevisionId, new ArrayList<>());
            this.map.get(affectedRevisionId).add(range);
        }

        public void addAllRangesToRevision(Long affectedRevisionId, List<SwhVulnerabilityRange> ranges){
            if(this.map.containsKey(affectedRevisionId)){
                this.map.get(affectedRevisionId).addAll(ranges);
            }else{
                this.map.put(affectedRevisionId, ranges);
            }
        }


        public void putAll(AffectedRevisions affectedRevisions){
            for (var entry : affectedRevisions.map.entrySet()){
                this.addAllRangesToRevision(entry.getKey(),entry.getValue());
            }
        }

        @Override
        public List<SwhVulnerabilityRange> getRevisionRange(Long revision) {
            return this.map.get(revision);
        }

        public void close(){
            
        }

}
