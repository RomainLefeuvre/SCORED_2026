package fr.inria.diverse.model;

import java.util.List;

public class VulnRangeListHashedExport extends HashedExport{
    private static final long serialVersionUID = 1L;
    private List<SwhVulnerabilityRange> list;
    public VulnRangeListHashedExport(int inputHash,List<SwhVulnerabilityRange> list) {

        super(inputHash);
        this.list=list;
    }

  

    public List<SwhVulnerabilityRange> getList() {
        return list;
    }
    
    
}
