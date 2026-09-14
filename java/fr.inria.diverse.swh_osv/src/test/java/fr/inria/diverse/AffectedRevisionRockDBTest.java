package fr.inria.diverse;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.rocksdb.RocksDBException;

import fr.inria.diverse.model.AffectedRevisionRockDb;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class AffectedRevisionRockDBTest {
  @Test  
  public void simpleInsertTest() throws RocksDBException{
    AffectedRevisionRockDb rdb = new AffectedRevisionRockDb(null);
    rdb.addRangeRevisionbyId(0L, 23);
    IntArrayList res = rdb.getRevisionRangesId(0L);
    assertEquals("Insertion failed",1, res.size());
    assertEquals("Get failed",23, res.getInt(0));
    rdb.close();
  }

}
