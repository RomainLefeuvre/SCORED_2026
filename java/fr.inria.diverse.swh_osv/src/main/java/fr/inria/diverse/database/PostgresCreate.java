package fr.inria.diverse.database;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.SwhBidirectionalGraph;

import com.github.luben.zstd.ZstdInputStream;
import com.google.common.collect.Lists;

import fr.inria.diverse.Utils.Progress;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import fr.inria.diverse.model.VulnerableProvenanceMap;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.tongfei.progressbar.ProgressBar;

public class PostgresCreate {
  final private static String url = "jdbc:postgresql://localhost:5433/swhosv";
  final private static String user = "postgres";
  final private static String password = "password";
  final private static String graphPath = "/dev/shm/compressed/graph";
  final private static String rangeToNewVulnerableCommitsPath = "/mnt/HDD/swh_osv/20_JAN_25/VulnerableRevisionAnalysis/Metric2.bin";
  final private static String metadataPath = "/mnt/HDD/swh_osv/origin-metadata.csv.zst";
  final private static String provenancePath = "/mnt/HDD/swh_osv/20_JAN_25/vulnerableProvenance/";

  private static final Logger logger = LogManager.getLogger(PostgresCreate.class);
  private static final int LINE_METADATA_NUMBER = 213581234;// hardcoded because it is much easier
  static ThreadLocal<SwhBidirectionalGraph> threadGraph = new ThreadLocal<>();
  SwhBidirectionalGraph graph;
  VulnerableProvenanceMap provenanceMap;
  // AffectedRevisionsOpti affectedRevisionsOpti;
  Map<SwhVulnerabilityRange, LongOpenHashSet> rangeToNewVulnerableCommits;

  HashMap<String, Integer> urlsToFetchToId;// urls of all the revs in the rangeToNewVulnerableCommits Map
  LongOpenHashSet originIdsToKeep;// urls that have more than a star and that are in the urlsToFetch

  public SwhBidirectionalGraph getThreadSafeeGraph() {
    if (threadGraph.get() == null) {
      threadGraph.set(graph.copy());
    }
    return threadGraph.get();
  }

  public PostgresCreate(String graphPath, String metadataPath, String vulnerableProvenancePath,
      String rangeToNewVulnerableCommitsPath)
      throws ClassNotFoundException, IOException {
    logger.info("loading graph");
    graph = SwhBidirectionalGraph.loadMapped(graphPath);

    graph.loadMessages();

    logger.info("Loading provenance Map");
    this.provenanceMap = new VulnerableProvenanceMap(graphPath, vulnerableProvenancePath, true);

    // logger.info("Loading affected revision");
    // this.affectedRevisionsOpti = (AffectedRevisionsOpti)
    // BinIO.loadObject(affectedRevisionsOptiPath);

    this.rangeToNewVulnerableCommits = (ConcurrentHashMap<SwhVulnerabilityRange, LongOpenHashSet>) BinIO
        .loadObject(rangeToNewVulnerableCommitsPath);

    logger.info("loading metadata");
    this.urlsToFetchToId = new HashMap<>();
    // this.urlsToFetchToId.put("github.com/coreos/go-oidc/v3", 0);// TODO debug
    // purposes:
    // remove
    this.originIdsToKeep = new LongOpenHashSet();
    loadUrlsToFetch();
    loadMetadata(metadataPath);
  }

  private void loadUrlsToFetch() {
    try (ProgressBar pb = Progress.infoBar("getting the urls to fetch", logger,
        this.provenanceMap.originIndexes.size())) {
      int i = 0;
      for (var origin : this.provenanceMap.originIndexes) {
        try {
          this.urlsToFetchToId.put(this.getThreadSafeeGraph().getUrl(origin), i);
        } catch (Exception e) {
          logger.info("error when adding origin node " + origin + " to the map");
          e.printStackTrace();
        }
        i++;
        pb.step();
      }
    }
  }

  public void start(Connection connection) throws SQLException, IOException, InterruptedException, ExecutionException {
    createTables();
    populateCVETable(connection);
    populateMappingTable(connection);

  }

  public void createTables() {

    // Create the table for cve mapping
    String createCveTableSQL = "CREATE TABLE IF NOT EXISTS ranges (" +
        "id BIGINT PRIMARY KEY, " +
        "cveid VARCHAR(25) NOT NULL," +
        "severity VARCHAR(128));";

    // Create the table for cve swhid mapping
    String createCveCommitMappingSQL = "CREATE TABLE IF NOT EXISTS cve_swhid_mapping (" +
        "range_id BIGINT NOT NULL," +
        "vulnerable_swhid CHAR(40) NOT NULL," +
        "PRIMARY KEY (range_id, vulnerable_swhid)," +
        "FOREIGN KEY (range_id) REFERENCES ranges(id));";

    // Establish connection, create statement, and execute queries
    try (Connection connection = DriverManager.getConnection(url, user, password);
        Statement statement = connection.createStatement()) {

      // Execute all create table queries
      statement.executeUpdate(createCveTableSQL);
      statement.executeUpdate(createCveCommitMappingSQL);

      System.out.println("Tables created successfully (if not exists).");

    } catch (SQLException e) {

      e.printStackTrace();
    }
  }

  /**
   * only load metadata for repos that have more than x stars and that have a new
   * vulnerable commit
   * 
   * @param metadataPath
   * @throws IOException
   */
  public void loadMetadata(String metadataPath) throws IOException {
    try (
        InputStream fileStream = new FileInputStream(metadataPath);
        InputStream zstdStream = new ZstdInputStream(fileStream);
        BufferedReader reader = new BufferedReader(new InputStreamReader(zstdStream, StandardCharsets.UTF_8));
        ProgressBar pb = Progress.infoBar("Filtering urls to keep only the ones with >0 stars and in urlstoFetch",
            logger,
            LINE_METADATA_NUMBER)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        String url = parts[1].replace("\"\"\"", "");
        int stars = Integer.parseInt(parts[4]);
        Integer indexurl = this.urlsToFetchToId.get(url);
        if (stars > 20 && indexurl != null) {
          this.originIdsToKeep.add(this.provenanceMap.originIndexes.getLong(indexurl));
        }
        pb.step();
      }
    }
    // clear data not needed anymore
    this.urlsToFetchToId = null;
  }

  private void populateCVETable(Connection conn) throws SQLException {
    String insertCveSQL = "INSERT INTO ranges (id,cveid,severity) VALUES (?,?,?) ON CONFLICT DO NOTHING";
    conn.setAutoCommit(false);

    try (PreparedStatement insertCveStmt = conn.prepareStatement(insertCveSQL);
        ProgressBar pb = Progress.infoBar("populating the cve database", logger, rangeToNewVulnerableCommits.size())) {
      long id = 0;

      for (var entry : rangeToNewVulnerableCommits.entrySet()) {

        pb.step();
        String cveId = entry.getKey().getVulnerabilityId();
        if (cveId.length() > 25) {
          continue;
        }
        String severity = entry.getKey().getSeverity();
        try {
          insertCveStmt.setLong(1, id);
          insertCveStmt.setString(2, cveId);
          insertCveStmt.setString(3, severity);
          insertCveStmt.addBatch();
        } catch (SQLException e) {
          e.printStackTrace();
        }
        id++;
      }
      insertCveStmt.executeBatch();// not enough cves to need to batch every x time
    }

  }

  public void populateMappingTable(Connection conn) throws SQLException, InterruptedException, ExecutionException {
    conn.setAutoCommit(false);

    try (ProgressBar pb = Progress.infoBar("populating the swhid and mapping local mapping table", logger,
        rangeToNewVulnerableCommits.size())) {

      ExecutorService executorService = Executors.newFixedThreadPool(100);
      AtomicInteger curRangeId = new AtomicInteger(-1);

      // Each task returns its local list
      List<Callable<List<FixedStringIntPair>>> tasks = rangeToNewVulnerableCommits.entrySet().stream()
          .map(entry -> (Callable<List<FixedStringIntPair>>) () -> {
            int threadLong = curRangeId.incrementAndGet();
            List<FixedStringIntPair> localList = new ArrayList<>();
            for (long revid : entry.getValue()) {
              LongOpenHashSet revOriginNodeIds = provenanceMap.getOriginNodeIds(revid);

              if (revOriginNodeIds.longStream().anyMatch(originIdsToKeep::contains)) {
                try {
                  String vulnswhid = getThreadSafeeGraph().getSWHID(revid).getSWHID().substring(10);
                  localList.add(new FixedStringIntPair(vulnswhid, threadLong));
                } catch (Exception e) {
                  logger.warn("getSWHID failed for revid", e);
                }
              }
            }
            pb.step();
            return localList;
          })
          .collect(Collectors.toList());

      try {
        List<List<Callable<List<FixedStringIntPair>>>> subTasks = Lists.partition(tasks, 500);
        logger.warn(subTasks.size() + " database subtasks created");

        for (var subtask : subTasks) {

          List<Future<List<FixedStringIntPair>>> futures = executorService.invokeAll(subtask);
          List<FixedStringIntPair> result = new ArrayList<>();
          for (Future<List<FixedStringIntPair>> future : futures) {
            result.addAll(future.get());
          }
          ConcurrentLinkedQueue<FixedStringIntPair> temp_table = new ConcurrentLinkedQueue<>(result);
          DatabaseMappingPopulator dbp = new DatabaseMappingPopulator(conn, temp_table);
          dbp.populateDatabase();
        }
      } catch (InterruptedException | ExecutionException e) {
        logger.error("Error during parallel task execution", e);
      } finally {
        executorService.shutdown();
      }
      // TODO: debug purpose, to delete
      // this.swhidTable = new ConcurrentLinkedQueue<>();
      // this.swhidTable.add(new
      // FixedStringIntPair("a7c457eacb849c163a496b29274242474a8f44ab", 0));
      // ConcurrentLinkedQueue<FixedStringIntPair> temp_table = new
      // ConcurrentLinkedQueue<>(this.swhidTable);
      // DatabaseMappingPopulator dbp = new DatabaseMappingPopulator(conn,
      // temp_table);
      // dbp.populateDatabase();
    }

  }

  public static void main(String[] args)
      throws ClassNotFoundException, IOException, SQLException, InterruptedException, ExecutionException {
    try (Connection connection = DriverManager.getConnection(url, user, password)) {

      PostgresCreate pgc = new PostgresCreate(graphPath, metadataPath, provenancePath, rangeToNewVulnerableCommitsPath);
      pgc.start(connection);
    }
  }

}