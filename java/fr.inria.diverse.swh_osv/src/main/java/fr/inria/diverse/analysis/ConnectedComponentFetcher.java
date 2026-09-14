package fr.inria.diverse.analysis;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import fr.inria.diverse.RangeAnalyzer;
import fr.inria.diverse.Utils.Progress;
import fr.inria.diverse.model.EventType;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.tongfei.progressbar.ProgressBar;

public class ConnectedComponentFetcher {

  final private static Logger logger = LogManager.getLogger(ConnectedComponentFetcher.class.getName());

  protected SwhBidirectionalGraph graph;
  protected List<SwhVulnerabilityRange> swhVulnerabilityRanges;
  static ThreadLocal<SwhBidirectionalGraph> threadGraph = new ThreadLocal<>();

  public ConnectedComponentFetcher(SwhBidirectionalGraph graph, List<SwhVulnerabilityRange> vulnRanges) {
    try {
      this.graph = graph;
      this.graph.loadMessages();
      this.swhVulnerabilityRanges = vulnRanges;
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  public SwhBidirectionalGraph getThreadSafeeGraph() {
    if (threadGraph.get() == null) {
      threadGraph.set(graph.copy());
      logger.info("Copying Graph");
    }
    return threadGraph.get();
  }

  public Set<Long> computeNodeInConnectedComponent() {

    Set<Long> visited = new HashSet<>();
    Stack<Long> stack = new Stack<>();
    try (ProgressBar pb = new ProgressBar("computing connected components", this.swhVulnerabilityRanges.size())) {
      for (SwhVulnerabilityRange range : this.swhVulnerabilityRanges) {
        pb.step();
        stack.addAll(range.getRevisionByEvent(EventType.INTRODUCED));

        // Forward traversal
        while (!stack.isEmpty()) {
          Long currentRevision = stack.pop();

          if (visited.contains(currentRevision)) {
            continue;
          }
          visited.add(currentRevision);
          final LazyLongIterator parentIt = graph.successors(currentRevision);
          for (long parentId; (parentId = parentIt.nextLong()) != -1;) {
            if (graph.getNodeType(parentId) == SwhType.REV && !visited.contains(parentId)) {
              stack.push(parentId);
            }
          }
          final LazyLongIterator childIt = graph.predecessors(currentRevision);
          for (long childId; (childId = childIt.nextLong()) != -1;) {
            if (graph.getNodeType(childId) == SwhType.REV && !visited.contains(childId)) {
              stack.push(childId);
            }
          }

        }
      }
    }
    return visited;

  }

  /**
   * compute a map containing the original commit and the cherry picked commit
   * 
   * @param connectedComponent
   * @return
   * @throws IOException
   */
  public HashMap<String, LongOpenHashSet> cherryPickCompute(Set<Long> connectedComponent) throws IOException {
    ConcurrentHashMap<Long, String> cherryPickedMap = new ConcurrentHashMap<>();
    try (ProgressBar pb = Progress.infoBar("computing cherry picked map",logger,connectedComponent.size())) {
      connectedComponent.parallelStream().forEach(id -> {
        pb.step();
        try {

          var message = this.graph.getMessage(id);

          if (message != null) {

            String currentMessage;
           
              currentMessage = IOUtils.toString(message, "UTF-8");
              if (currentMessage.contains("cherry picked from commit ")) {
                String cherryPickedHash = currentMessage.split("cherry picked from commit ")[1].split("\\)")[0];

                cherryPickedMap.put(id, cherryPickedHash);
              }

          }
        } catch (Exception e) {
          logger.info(e.getMessage());
        }
      });
    }
    ;
    logger.info(cherryPickedMap.size());

    HashMap<String, LongOpenHashSet> result = new HashMap<>();
    cherryPickedMap.forEach((k, v) -> {
      if (!result.containsKey(v)) {
        result.put(v, new LongOpenHashSet());
      }
      LongOpenHashSet currentList = result.get(v);

      currentList.add(k);
    });
    return result;

  }

  protected static JSAPResult parse_args(final String[] args) {
    JSAPResult config = null;
    try {
      final SimpleJSAP jsap = new SimpleJSAP(RangeAnalyzer.class.getName(),
          "Computes a map of cherry picked commit to their original commit",
          new Parameter[] {
              new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                  "graphPath", "Basename of the compressed graph"),
              new FlaggedOption("allRanges", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'r',
                  "allRanges",
                  "Path to the binary containing the list of all Ranges(List<SwhVulnerabilityRange>)"),
          });

      config = jsap.parse(args);
      if (jsap.messagePrinted()) {
        System.exit(1);
      }
    } catch (final JSAPException e) {
      e.printStackTrace();
    }
    return config;
  }

  public static void main(String[] args) throws IOException, ClassNotFoundException {
    /*
     * final JSAPResult config = parse_args(args);
     * 
     * final String graphPath = config.getString("graphPath");
     * final String allRangesPath = config.getString("allRanges");
     * 
     * List<SwhVulnerabilityRange> allRanges = ((List<SwhVulnerabilityRange>)
     * BinIO.loadObject(allRangesPath));
     * 
     * ConnectedComponentFetcher fetcher = new ConnectedComponentFetcher(graphPath,
     * allRanges);
     * Set<Long> connectedComponent = fetcher.computeNodeInConnectedComponent();
     * 
     * HashMap<String, List<String>> cherryPickedMap =
     * fetcher.cherryPickCompute(connectedComponent);
     * 
     * BinIO.storeObject(cherryPickedMap, "./outputs/cherypicked-map.bin");
     */
  }

}
