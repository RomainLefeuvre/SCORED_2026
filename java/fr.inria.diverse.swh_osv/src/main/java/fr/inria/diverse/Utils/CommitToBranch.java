package fr.inria.diverse.Utils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.json.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;
import org.softwareheritage.graph.labels.DirEntry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import fr.inria.diverse.OsvLabel;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.labelling.ArcLabelledNodeIterator.LabelledArcIterator;
import me.tongfei.progressbar.ProgressBar;

public class CommitToBranch {
  private static final Logger logger = LogManager.getLogger(OsvLabel.class);

  final private static String GRAPH_PATH_STRING = "/dev/shm/compressed/graph";
  private SwhBidirectionalGraph graph;
  private Map<String, Set<String>> swhidToBranchName;
  private List<String> swhids;

  public CommitToBranch(List<String> swhids) throws IOException {
    this.graph = SwhBidirectionalGraph.loadLabelledMapped(GRAPH_PATH_STRING);
    this.graph.loadLabelNames();
    this.swhidToBranchName = new HashMap<>();
    this.swhids = swhids;
  }


  private static List<String> extractIdsFromFile(String filePath) throws IOException {
    // Create a Gson instance
    Gson gson = new Gson();

    // Read the JSON file
    FileReader reader = new FileReader(filePath);

    // Parse the JSON array
    JsonArray jsonArray = gson.fromJson(reader, JsonArray.class);
    reader.close();
    Set<String> heads = new HashSet<String>();
    // Extract _id values into a List of Strings
    for (JsonElement element : jsonArray) {
      com.google.gson.JsonArray subArray = element.getAsJsonArray();
      com.google.gson.JsonArray subsubAray = subArray.asList().get(1).getAsJsonArray();
      for (JsonElement subElement : subsubAray) {
        String head = subElement.getAsString();
        heads.add(head);
      }
    }

    return heads.stream().collect(Collectors.toList());
  }

  public CommitToBranch compute() {
    for (String swhid : ProgressBar.wrap(this.swhids, Progress.infoBarBuilder("finding branches!", logger))) {
      Set<String> branches = getBranches(swhid);
      if (!branches.isEmpty()) {
        this.swhidToBranchName.put(swhid, branches);
      }

    }
    return this;
  }

  public void export(String fileName) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Path outputPath = Paths.get(fileName);

    try (FileWriter writer = new FileWriter(outputPath.toFile(), StandardCharsets.UTF_8)) {
      gson.toJson(this.swhidToBranchName, writer);
      System.out.println("Exported data to: " + outputPath.toAbsolutePath());
    } catch (IOException e) {
      System.err.println("Error exporting data: " + e.getMessage());
    }
  }

  private Set<String> getBranches(String commitId) {
    Set<String> branches = new HashSet<>();
    LabelledArcIterator labelsIterator = graph
        .labelledPredecessors(graph.getNodeId(commitId));
    Long child;
    while ((child = labelsIterator.nextLong()) != -1) {
      if (graph.getNodeType(child) == SwhType.SNP) {
        DirEntry[] labels = (DirEntry[]) labelsIterator.label().get();
        for (DirEntry label : labels) {
          String entryName = new String(graph.getLabelName(label.filenameId));
          if (!entryName.contains("refs/pull/"))
            branches.add(entryName);
        }
      }

    }
    return branches;

  }
}
