package fr.inria.diverse.database;

import java.sql.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.inria.diverse.Utils.Progress;
import me.tongfei.progressbar.ProgressBar;

import java.util.ArrayList;
import java.util.List;

public class DatabaseMappingPopulator {

    private final ExecutorService executorService;
    private final Connection conn;
    private final ConcurrentLinkedQueue<FixedStringIntPair> swhidTable;
    String insertMappingSQL = "INSERT INTO cve_swhid_mapping (range_id, vulnerable_swhid) VALUES (?, ?);";
    final private static Logger logger = LogManager.getLogger(DatabaseMappingPopulator.class.getName());


    public DatabaseMappingPopulator(Connection conn, ConcurrentLinkedQueue<FixedStringIntPair> swhidTable) {
        this.conn = conn;
        this.swhidTable = swhidTable;
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
    public void populateDatabase() throws SQLException, InterruptedException, ExecutionException {
        int batchSize = 1000;
        List<Future<Void>> futures = new ArrayList<>();
        try (ProgressBar pb = Progress.infoBar("populating the db", logger, this.swhidTable.size())) {

            for (int t = 0; t < Runtime.getRuntime().availableProcessors(); t++) {
                futures.add(executorService.submit(() -> {
                    try (PreparedStatement insertMappingStmt = conn.prepareStatement(insertMappingSQL)) {
                        conn.setAutoCommit(false);
                        List<FixedStringIntPair> localBatch = new ArrayList<>(batchSize);
                        FixedStringIntPair entry;

                        while ((entry = swhidTable.poll()) != null) {
                            localBatch.add(entry);
                            if (localBatch.size() >= batchSize) {
                                insertBatch(insertMappingStmt, localBatch);
                                conn.commit();
                                localBatch.clear();
                            }
                            pb.step();
                        }

                        if (!localBatch.isEmpty()) {
                            insertBatch(insertMappingStmt, localBatch);
                            conn.commit();
                        }
                    } catch (SQLException e) {
                        e.printStackTrace(); // Handle properly
                    }
                    return null;
                }));
            }

            for (Future<Void> f : futures)
                f.get();
        }
        executorService.shutdown();

    }
  
  private void insertBatch(PreparedStatement stmt, List<FixedStringIntPair> batch) throws SQLException {
      for (FixedStringIntPair pair : batch) {
          stmt.setLong(1, pair.getValue());
          stmt.setString(2, new String(pair.getKey()));
          stmt.addBatch();
      }
      stmt.executeBatch();
  }
}

